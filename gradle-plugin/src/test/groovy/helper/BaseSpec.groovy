package helper

import akka.grpc.gradle.AkkaGrpcPluginExtension
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static akka.grpc.gradle.AkkaGrpcPluginExtension.getPLUGIN_CODE

abstract class BaseSpec extends Specification {

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder()

    File srcDir

    File buildDir

    File buildFile

    def createBuildFolder() {
        srcDir = projectDir.newFolder("src", "main", "proto")
        buildDir = projectDir.newFolder("build")
    }

    def generateBuildScripts() {
        buildFile = projectDir.newFile("build.gradle")
        buildFile.text = """
plugins {
  id 'scala'
  id '$PLUGIN_CODE'
}
repositories { jcenter() }
"""
    }

    def findAkkaGrpcRuntime() {
        this.project.configurations.akkaGrpcRuntime.allDependencies.find { it.name.contains("akka-grpc-runtime") }
    }

    AkkaGrpcPluginExtension sampleSetup(def plugin = "scala", String scala = "2.12") {
        project.pluginManager.apply plugin
        project.pluginManager.apply PLUGIN_CODE
        project.repositories { mavenCentral() }
        project.dependencies {
            implementation "com.typesafe.scala-logging:scala-logging_$scala:3.9.2"
        }
        project.extensions.getByType(AkkaGrpcPluginExtension)
    }
}
