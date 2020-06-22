package helper


import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static akka.grpc.gradle.AkkaGrpcPluginExtension.DEFAULT_SCALA_VERSION

abstract class BaseUnitTest extends BaseTest {

    Project project

    AkkaGrpcPluginExtension akkaGrpcExt

    def setup() {
        createBuildFolder()

        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
        project.pluginManager.apply pluginLanguage()
        project.pluginManager.apply 'com.lightbend.akka.grpc.gradle'

        akkaGrpcExt = project.extensions.getByType(AkkaGrpcPluginExtension)
    }

    def findAkkaGrpcRuntime() {
        this.project.configurations.implementation.allDependencies.find { it.name.contains("akka-grpc-runtime") }
    }

    abstract String pluginLanguage()
}
