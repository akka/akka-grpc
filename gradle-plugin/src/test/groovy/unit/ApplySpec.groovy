package unit

import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseSpec
import helper.ScalaWrapperPlugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import static akka.grpc.gradle.AkkaGrpcPluginExtension.PLUGIN_CODE

class ApplySpec extends BaseSpec {

    Project project

    def setup() {
        createBuildFolder()
        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
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
    }

    def "should fail if multiple scala-library declared"() {
        given:
        project.pluginManager.apply "scala"
        project.pluginManager.apply PLUGIN_CODE
        and:
        project.repositories { mavenCentral() }
        project.dependencies {
            implementation group: 'org.mockito', name: 'mockito-scala_2.11', version: '1.6.1'
            implementation group: 'org.mockito', name: 'mockito-scala_2.13', version: '1.14.8'
        }
        when:
        project.evaluate()
        then:
        def ex = thrown(ProjectConfigurationException)
        ex.cause.message.startsWith "$PLUGIN_CODE requires a single major.minor version of all scala libraries in compileClasspath."
    }
}
