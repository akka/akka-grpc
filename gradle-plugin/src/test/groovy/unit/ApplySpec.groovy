package unit

import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseSpec
import helper.ScalaWrapperPlugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.testfixtures.ProjectBuilder

import static akka.grpc.gradle.AkkaGrpcPluginExtension.PLUGIN_CODE

class ApplySpec extends BaseSpec {

    Project project

    def setup() {
        createBuildFolder()
        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
    }

    def "should success if scala or java plugins are applied and a single scala-library declared"() {
        given:
        def akkaGrpcExt = sampleSetup(plugin, scala)
        when:
        project.evaluate()
        then:
        with(akkaGrpcExt) {
            it.pluginVersion == System.getProperty("akkaGrpcTest.pluginVersion")
            it.language == lang
        }
        where:
        plugin            || scala || lang
        "java"             | "2.12" | "Java"
        "java"             | "2.13" | "Java"
        "scala"            | "2.12" | "Scala"
        "scala"            | "2.13" | "Scala"
        ScalaWrapperPlugin | "2.12" | "Scala"
        ScalaWrapperPlugin | "2.13" | "Scala"
    }

    def "should fail if neither scala nor java plugins are applied"() {
        when:
        project.pluginManager.apply PLUGIN_CODE
        then:
        def ex = thrown(PluginApplicationException)
        ex.message.contains PLUGIN_CODE
        and:
        ex.cause instanceof ObjectInstantiationException
        ex.cause.message.contains(AkkaGrpcPluginExtension.name)
        and:
        ex.cause.cause.message == "$PLUGIN_CODE requires either `java` or `scala` plugin to be applied before the plugin."
    }

    def "should fail if scala or java plugins are applied before the plugin"() {
        when:
        project.pluginManager.apply PLUGIN_CODE
        project.pluginManager.apply plugin
        then:
        def ex = thrown(PluginApplicationException)
        ex.message.contains PLUGIN_CODE
        and:
        ex.cause instanceof ObjectInstantiationException
        ex.cause.message.contains(AkkaGrpcPluginExtension.name)
        and:
        ex.cause.cause.message == "$PLUGIN_CODE requires either `java` or `scala` plugin to be applied before the plugin."
        where:
        plugin << ["java", "scala", ScalaWrapperPlugin]
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
