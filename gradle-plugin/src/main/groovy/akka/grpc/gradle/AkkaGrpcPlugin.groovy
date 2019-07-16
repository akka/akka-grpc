package akka.grpc.gradle

import org.apache.commons.lang.SystemUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.regex.Matcher
import java.util.regex.Pattern

class AkkaGrpcPlugin implements Plugin<Project>, DependencyResolutionListener {

    final String pluginVersion = AkkaGrpcPlugin.class.package.implementationVersion

    final String protocVersion = "3.4.0"
    final String grpcVersion = "1.20.0" // checked synced by GrpcVersionSyncCheckPlugin

    Project project

    private void mkBatLauncherScript(File jarFile) {
        String script = "@echo off\n" +
                "java -jar \"%~dp0" + jarFile.getName() + "\" %*\n" +
                "exit /B %errorlevel%\n"
        mkLauncherScript(jarFile, "bat", script, false)
    }

    private void mkLauncherScript(File jarFile, String extension, String contents, boolean posix) {
        File shFile = new File(jarFile.getParentFile(), replaceFileExtension(jarFile.getName(), extension))
        Files.writeString(shFile.toPath(), contents, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

        if (posix) {
            Set<PosixFilePermission> perms = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(shFile.toPath(), perms)
        }
    }

    private String replaceFileExtension(String filename, String newExtension) {
        int ind = filename.lastIndexOf('.')
        if (ind < 0) return filename + "." + newExtension
        else return filename.substring(0, ind + 1) + newExtension
    }

    private String renderLogPath(File logFile) {
        String candidate = logFile.toPath().toAbsolutePath().toRealPath().toString()
        if (!SystemUtils.IS_OS_WINDOWS) {
            return candidate
        } else {
            Pattern ptnWinPath = Pattern.compile("(?i)^[a-z]:(\\\\.+)")
            Matcher matcher = ptnWinPath.matcher(candidate)
            if (matcher.matches()) {
                return matcher.group(1).replace("\\", "/")
            } else {
                return candidate.replace("\\", "/")
            }
        }
    }

    @Override
    void apply(Project project) {
        this.project = project
        project.gradle.addListener(this)

        def extension = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)
        String assemblySuffix = SystemUtils.IS_OS_WINDOWS ? "bat" : "jar"

        Configuration assembliesConfig = project.getConfigurations().create("codegen-assemblies");
        assembliesConfig.setTransitive(false)
        project.getDependencies().add(assembliesConfig.getName(), "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:${pluginVersion}:assembly")
        project.getDependencies().add(assembliesConfig.getName(), "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:${pluginVersion}:assembly")

        if (SystemUtils.IS_OS_WINDOWS) {
            for (File assembly : assembliesConfig.getFiles()) {
                mkBatLauncherScript(assembly)
            }
        }

        project.configure(project) {
            boolean isScala = "${extension.language}".toLowerCase() == "scala"
            File logFile = File.createTempFile("akka-grpc-gradle", ".log")
            logFile.deleteOnExit()

            apply plugin: 'com.google.protobuf'
            protobuf {
                protoc {
                    // Get protobuf from maven central instead of
                    // using the installed version:
                    artifact = "com.google.protobuf:protoc:${protocVersion}"
                }

                plugins {
                    akkaGrpc {
                        artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:${pluginVersion}:assembly@${assemblySuffix}"
                    }
                    if (isScala) {
                        scalapb {
                            artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:${pluginVersion}:assembly@${assemblySuffix}"
                        }
                    }
                }
                sourceSets {
                    main {
                        proto {
                            srcDir 'src/main/protobuf'
                            srcDir 'src/main/proto'
                            // Play conventions:
                            srcDir 'app/protobuf'
                            srcDir 'app/proto'
                        }
                    }
                }

                if (isScala) {
                    sourceSets {
                        main {
                            scala {
                                srcDir 'build/generated/source/proto/main/akkaGrpc'
                                srcDir 'build/generated/source/proto/main/scalapb'
                            }
                        }
                    }
                }

                generateProtoTasks {
                    all().each { task ->
                        if (isScala) {
                            task.builtins {
                                remove java
                            }
                        }

                        task.plugins {
                            akkaGrpc {
                                option "language=${extension.language}"
                                option "generate_client=${extension.generateClient}"
                                option "generate_server=${extension.generateServer}"
                                option "server_power_apis=${extension.serverPowerApis}"
                                option "use_play_actions=${extension.usePlayActions}"
                                option "extra_generators=${extension.extraGenerators.join(';')}"
                                option "logfile=${renderLogPath(logFile)}"
                                if (extension.generatePlay) {
                                    option "generate_play=true"
                                }
                                if (isScala) {
                                    option "flat_package"
                                }
                            }
                            if (isScala) {
                                scalapb {
                                    option "flat_package"
                                }
                            }
                        }
                    }
                }
            }

            println project.getTasks()
            project.task("printProtocLogs") {
                doLast {
                    Files.lines(logFile.toPath()).forEach { line ->
                        if (line.startsWith("[info]")) logger.info(line.substring(7))
                        else if (line.startsWith("[debug]")) logger.debug(line.substring(7))
                        else if (line.startsWith("[warn]")) logger.warn(line.substring(6))
                        else if (line.startsWith("[error]")) logger.error(line.substring(7))
                    }
                }
            }
            project.getTasks().getByName("compileJava").dependsOn("printProtocLogs")
        }
    }


    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        def compileDeps = project.getConfigurations().getByName("compile").getDependencies()
        compileDeps.add(project.getDependencies().create("com.lightbend.akka.grpc:akka-grpc-runtime_2.12:${pluginVersion}"))
        // TODO #115 grpc-stub is only needed for the client. Can we use the 'suggestedDependencies' somehow?
        compileDeps.add(project.getDependencies().create("io.grpc:grpc-stub:${grpcVersion}"))
        project.gradle.removeListener(this)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {

    }
}

class AkkaGrpcPluginExtension {

    String language
    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    List<String> extraGenerators = [ ]

    AkkaGrpcPluginExtension(Project project) {
        if (project.plugins.hasPlugin("scala"))
            language = "Scala"
        else
            language = "Java"
    }
}
