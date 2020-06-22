package helper

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class BaseTest extends Specification {
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
  id 'com.lightbend.akka.grpc.gradle'
}
repositories { jcenter() }
"""
    }
}
