package akka.grpc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import java.io.File
import java.nio.file.Files

class AkkaGrpcPlugin implements Plugin<Project>, DependencyResolutionListener {

    final String pluginVersion = AkkaGrpcPlugin.class.package.implementationVersion

    final String protocVersion = "3.4.0"
    final String grpcVersion = "1.20.0" // checked synced by GrpcVersionSyncCheckPlugin

    Project project

    @Override
    void apply(Project project) {
        this.project = project
        project.gradle.addListener(this)

        def extension = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)

        project.configure(project) {
            boolean isScala = "${extension.language}".toLowerCase() == "scala"
            boolean isJava = "${extension.language}".toLowerCase() == "java"
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
                        artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:${pluginVersion}:assembly@jar"
                    }
                    if (isScala) {
                        scalapb {
                            artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:${pluginVersion}:assembly@jar"
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

                sourceSets {
                    main {
                        if (isScala) {
                            scala {
                                srcDir 'build/generated/source/proto/main/akkaGrpc'
                                srcDir 'build/generated/source/proto/main/scalapb'
                            }
                        }
                        if (isJava) {
                            java {
                                srcDir 'build/generated/source/proto/main/akkaGrpc'
                                srcDir 'build/generated/source/proto/main/java'
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
                                option "logfile=${logFile.getAbsolutePath()}"
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
