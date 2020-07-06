package akka.grpc.gradle

import com.google.protobuf.gradle.ProtobufPlugin
import org.apache.commons.lang.SystemUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber

import java.nio.file.Files
import java.nio.file.Path

import static akka.grpc.gradle.AkkaGrpcPluginExtension.*

class AkkaGrpcPlugin implements Plugin<Project> {

    Project project

    @Override
    void apply(Project project) {

        if (VersionNumber.parse(GradleVersion.current().version) < VersionNumber.parse("5.6")) {
            throw new GradleException("Gradle version is ${GradleVersion.current().version}. Minimum supported version is 5.6")
        }

        this.project = project
        def akkaGrpcExt = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)

        if (akkaGrpcExt.scala) {
            project.pluginManager.apply ScalaPlugin
        } else {
            project.pluginManager.apply JavaPlugin
        }

        project.pluginManager.apply ProtobufPlugin

        String assemblySuffix = SystemUtils.IS_OS_WINDOWS ? "bat" : "jar"
        String assemblyClassifier = SystemUtils.IS_OS_WINDOWS ? "bat" : "assembly"

        Path logFile = project.buildDir.toPath().resolve("akka-grpc-gradle-plugin.log")

        project.sourceSets {
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

        project.sourceSets {
            main {
                if (akkaGrpcExt.scala) {
                    scala {
                        srcDir 'build/generated/source/proto/main/akkaGrpc'
                        srcDir 'build/generated/source/proto/main/scalapb'
                    }
                } else {
                    java {
                        srcDir 'build/generated/source/proto/main/akkaGrpc'
                        srcDir 'build/generated/source/proto/main/java'
                    }
                }
            }
            //TODO add test sources
        }

        // workaround for func tests, when only need to tests a plugin without rebuilding dependencies
        def baselineVersion = System.getProperty("akkaGrpcTest.baselineVersion", akkaGrpcExt.pluginVersion)

        project.protobuf {
            protoc {
                // Get protobuf from maven central instead of
                // using the installed version:
                artifact = "com.google.protobuf:protoc:${PROTOC_VERSION}"
            }
            plugins {
                akkaGrpc {
                    artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_$PROTOC_PLUGIN_SCALA_VERSION:${baselineVersion}:${assemblyClassifier}@${assemblySuffix}"
                }
                if (akkaGrpcExt.scala) {
                    scalapb {
                        artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_$PROTOC_PLUGIN_SCALA_VERSION:${baselineVersion}:${assemblyClassifier}@${assemblySuffix}"
                    }
                }
            }
            generateProtoTasks {
                all().each { task ->
                    if (akkaGrpcExt.scala) {
                        task.builtins {
                            remove java
                        }
                    }

                    task.plugins {
                        akkaGrpc {
                            option "language=${akkaGrpcExt.scala ? "Scala" : "Java"}"
                            option "generate_client=${akkaGrpcExt.generateClient}"
                            option "generate_server=${akkaGrpcExt.generateServer}"
                            option "server_power_apis=${akkaGrpcExt.serverPowerApis}"
                            option "use_play_actions=${akkaGrpcExt.usePlayActions}"
                            option "extra_generators=${akkaGrpcExt.extraGenerators.join(';')}"
                            option "logfile=${project.projectDir.toPath().relativize(logFile).toString()}"
                            if (akkaGrpcExt.generatePlay) {
                                option "generate_play=true"
                            }
                            if (akkaGrpcExt.scala) {
                                option "flat_package"
                            }
                        }
                        if (akkaGrpcExt.scala) {
                            scalapb {
                                option "flat_package"
                            }
                        }
                    }
                }
            }
        }

        project.afterEvaluate { Project p ->
            def scalaVersion = autodetectScala()
            p.dependencies {
                implementation "com.lightbend.akka.grpc:akka-grpc-runtime_${scalaVersion}:${baselineVersion}"
                implementation "io.grpc:grpc-stub:${GRPC_VERSION}"
            }
        }

        project.task("printProtocLogs") {
            doLast {
                Files.lines(logFile).forEach { line ->
                    if (line.startsWith("[info]")) logger.info(line.substring(7))
                    else if (line.startsWith("[debug]")) logger.debug(line.substring(7))
                    else if (line.startsWith("[warn]")) logger.warn(line.substring(6))
                    else if (line.startsWith("[error]")) logger.error(line.substring(7))
                }
            }
        }
        project.getTasks().getByName("compileJava").dependsOn("printProtocLogs") //TODO logs for multi project builds

    }

    String autodetectScala() {
        def deps = project.configurations.compileClasspath.copyRecursive().resolvedConfiguration
            .resolvedArtifacts.collect { it.moduleVersion.id }

        def scalaLibs = deps.findAll { it.group == "org.scala-lang" && it.name == "scala-library" }
            .collect { it.version.split('\\.').init().join(".") }.unique()
        if (scalaLibs.size() != 1) {
            throw new GradleException("$PLUGIN_CODE requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath.\nFound $scalaLibs")
        }

        def scalaLibs2 = deps.findAll { it.name.contains("_") }.collect { it.name.split("_").last() }.unique()
        if (scalaLibs2.size() != 1) {
            throw new GradleException("$PLUGIN_CODE requires a single major.minor version of all scala libraries in compileClasspath.\nFound $scalaLibs2")
        }

        scalaLibs2.first()
    }
}

