package akka.grpc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

class AkkaGrpcPlugin implements Plugin<Project>, DependencyResolutionListener {

    final String pluginVersion = AkkaGrpcPlugin.class.package.implementationVersion

    final String protocVersion = "3.4.0"
    final String grpcVersion = "1.12.0"

    Project project

    @Override
    void apply(Project project) {
        this.project = project
        project.gradle.addListener(this)

        def extension = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)

        project.configure(project) {
            boolean isScala = "${extension.language}".toLowerCase() == "scala"

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

                if (isScala) {
                    sourceSets {
                        main {
                            proto {
                                srcDir 'src/main/protobuf'
                            }
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

    AkkaGrpcPluginExtension(Project project) {
        if (project.plugins.hasPlugin("scala"))
            language = "Scala"
        else
            language = "Java"

    }
}
