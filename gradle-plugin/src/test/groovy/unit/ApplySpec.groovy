package unit

import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseSpec
import helper.ScalaWrapperPlugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static akka.grpc.gradle.AkkaGrpcPluginExtension.PLUGIN_CODE

class ApplySpec extends BaseSpec {

    Project project

    File log

    def setup() {
        createBuildFolder()
        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
    }

    def createLog() {
        log = projectDir.newFile("build/akka-grpc-gradle-plugin.log")
    }

    BuildResult executeGradleTask(String task) {
        def runner = GradleRunner.create().forwardOutput()
            .withProjectDir(projectDir.root)
            .withArguments("--stacktrace", task)
            .withPluginClasspath()
            .withDebug(true)
        runner.build()
    }

    @Unroll
    def "should detect language for #plugin"() {
        given:
        def akkaGrpcExt = sampleSetup(plugin, scala)
        when:
        project.evaluate()
        then:
        with(akkaGrpcExt) {
            it.pluginVersion == System.getProperty("akkaGrpcTest.pluginVersion")
            it.scala == isScala
        }
        where:
        plugin            || scala || isScala
        "java"             | "2.12" | false
        "java"             | "2.13" | false
        "scala"            | "2.12" | true
        "scala"            | "2.13" | true
        ScalaWrapperPlugin | "2.12" | true
        ScalaWrapperPlugin | "2.13" | true
    }

    def "should fail if no scala-library declared"() {
        given:
        project.pluginManager.apply "scala"
        project.pluginManager.apply PLUGIN_CODE
        when:
        project.evaluate()
        then:
        def ex = thrown(ProjectConfigurationException)
        ex.cause.message.startsWith "$PLUGIN_CODE requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."
        ex.cause.message.endsWith "Found []"
    }

    def "should fail if multiple scala-library declared"() {
        given:
        project.pluginManager.apply PLUGIN_CODE
        and:
        project.dependencies {
            implementation group: 'org.mockito', name: 'mockito-scala_2.11', version: '1.6.1'
            implementation group: 'org.mockito', name: 'mockito-scala_2.13', version: '1.14.8'
        }
        when:
        project.evaluate()
        then:
        def ex = thrown(ProjectConfigurationException)
        ex.cause.message.startsWith "$PLUGIN_CODE requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."
        ex.cause.message.endsWith "Found [2.11, 2.13]"
    }

    def "should not fail scala autodetect if dependencies contain underscore"() {
        given:
        def akkaGrpcExt = sampleSetup()
        and:
        project.dependencies {
            implementation "com.google.errorprone:error_prone_annotations:2.3.4"
        }
        when:
        project.evaluate()
        then: 'plugin is applied'
        akkaGrpcExt.scala
    }

    def "should disable compileJava if no java source files found"() {
        given:
        sampleSetup()
        generateBuildScripts()
        createLog()
        when:
        project.evaluate()
        BuildResult result = executeGradleTask("compileJava")
        then: "compileJava is enabled, but task was skipped"
        project.tasks.getByName("compileJava").enabled
        result.task(":compileJava").outcome == SKIPPED
    }

    def "should enable compileJava if java source files found"() {
        given:
        sampleSetup()
        generateBuildScripts()
        createLog()
        when:
        def javaDir = projectDir.newFolder('src', 'main', 'java')
        new File(javaDir, "Empty.java").text = "final class Empty {}"
        and:
        BuildResult result = executeGradleTask("compileJava")
        then: "compileJava is success"
        result.task(":compileJava").outcome == SUCCESS
    }

    def "should enable compileJava if project has only generated java source files"() {
        given:
        sampleSetup("kotlin")
        when:
        def kotlinDir = projectDir.newFolder('src', 'main', 'kotlin')
        new File(kotlinDir, "Empty.kt").text = "object Empty {}"
        and:
        project.evaluate()
        then: "compileJava is enabled"
        project.tasks.getByName("compileJava").enabled
    }

    def "should allow implicit declarations of scala-library version"() {
        given:
        project.pluginManager.apply PLUGIN_CODE
        project.dependencies {
            constraints {
                implementation "org.scala-lang:scala-library:2.13.1"
            }
            implementation "org.scala-lang:scala-library"
        }
        when:
        project.evaluate()
        then:
        project.extensions.getByType(AkkaGrpcPluginExtension)
    }

    def "should fail if scala-version implicitly declared and mismatches with other scala-library version"() {
        given:
        project.pluginManager.apply PLUGIN_CODE
        project.dependencies {
            constraints {
                implementation "org.scala-lang:scala-library:2.12.0"
            }
            implementation "com.typesafe.scala-logging:scala-logging_2.13:3.9.2"
        }
        when:
        project.evaluate()
        then:
        def ex = thrown(ProjectConfigurationException)
        ex.cause.message.startsWith "$PLUGIN_CODE requires a single major.minor version of `org.scala-lang:scala-library` in compileClasspath."
        ex.cause.message.endsWith "Found [2.12, 2.13]"
    }
}
