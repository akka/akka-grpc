package akka.grpc.gradle

import org.apache.commons.lang.SystemUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber

import java.nio.file.Files
import java.nio.file.Path

class AkkaGrpcPlugin implements Plugin<Project> {

    final String protocVersion = "3.4.0"
    final String grpcVersion = "1.30.0" // checked synced by GrpcVersionSyncCheckPlugin

    Project project

    @Override
    void apply(Project project) {

        if (VersionNumber.parse(GradleVersion.current().version) < VersionNumber.parse("5.6")) {
            throw new GradleException("Gradle version is ${GradleVersion.current().version}. Minimum supported version is 5.6")
        }

        this.project = project
        project.gradle.addListener(this)

        def extension = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)

        String assemblySuffix = SystemUtils.IS_OS_WINDOWS ? "bat" : "jar"
        String assemblyClassifier = SystemUtils.IS_OS_WINDOWS ? "bat" : "assembly"

        Path logFile = project.buildDir.toPath().resolve("akka-grpc-gradle-plugin.log")

        boolean isScala = "${extension.language}".toLowerCase() == "scala"
        boolean isJava = "${extension.language}".toLowerCase() == "java"

        project.pluginManager.apply 'com.google.protobuf'

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

        project.protobuf {
            protoc {
                // Get protobuf from maven central instead of
                // using the installed version:
                artifact = "com.google.protobuf:protoc:${protocVersion}"
            }

            plugins {
                akkaGrpc {
                    artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:${extension.pluginVersion}:${assemblyClassifier}@${assemblySuffix}"
                }
                if (isScala) {
                    scalapb {
                        artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:${extension.pluginVersion}:${assemblyClassifier}@${assemblySuffix}"
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
                            option "logfile=${project.projectDir.toPath().relativize(logFile).toString()}"
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

        project.getTasks().getByName("compileJava").dependsOn("printProtocLogs")

        project.dependencies {
            implementation "com.lightbend.akka.grpc:akka-grpc-runtime_2.12:${extension.pluginVersion}"

            // TODO #115 grpc-stub is only needed for the client. Can we use the 'suggestedDependencies' somehow?
            implementation "io.grpc:grpc-stub:${grpcVersion}"
        }
    }
}

