package unit

import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseUnitTest
import org.gradle.api.artifacts.Dependency

import static akka.grpc.gradle.AkkaGrpcPluginExtension.DEFAULT_SCALA_VERSION

class AkkaGrpcPluginScalaTest extends BaseUnitTest {

    def "should create akkaGrpc extension with default values"() {
        expect:
        with(akkaGrpcExt) {
            it instanceof AkkaGrpcPluginExtension
            it.pluginVersion == System.getProperty("akkaGrpc.version")
            it.scalaVersion == DEFAULT_SCALA_VERSION
            it.language.equalsIgnoreCase("scala")
        }
    }

    def "should disallow overriding pluginVersion via extension"() {
        given:
        def version = "3.2.1.2"
        when:
        project.akkaGrpc { pluginVersion = version }
        then:
        def ex = thrown(GroovyRuntimeException)
        ex.message.contains("Cannot set the value of read-only property 'pluginVersion'")
    }

    def "should add akka-grpc-runtime with default pluginVersion and scalaVersion"() {
        when:
        project.evaluate()
        then:
        def d = findAkkaGrpcRuntime()
        d.version == akkaGrpcExt.pluginVersion
        d.name.endsWith "_$DEFAULT_SCALA_VERSION"
    }

    def "should override scalaVersion of akka-grpc-runtime via extension"() {
        given:
        def myVer = "qwe"
        when:
        project.akkaGrpc { scalaVersion = myVer }
        and:
        project.evaluate()
        then:
        akkaGrpcExt.scalaVersion == myVer
        and:
        def d = findAkkaGrpcRuntime()
        d.name.endsWith "_${myVer}"
    }

    def "should add protobuf plugins with default pluginVersion and scalaVersion"() {
        when:
        project.evaluate()
        then:
        Map<String, Dependency> plugins = project.protobuf.tools.plugins.collectEntries { [(it.name): project.dependencies.create(it.artifact)] }
        plugins.keySet().sort() == ["akkaGrpc", "scalapb"]

        with(plugins.akkaGrpc) {
            group == "com.lightbend.akka.grpc"
            name == "akka-grpc-codegen_$DEFAULT_SCALA_VERSION"
            version == akkaGrpcExt.pluginVersion
        }

        with(plugins.scalapb) {
            group == "com.lightbend.akka.grpc"
            name == "akka-grpc-scalapb-protoc-plugin_$DEFAULT_SCALA_VERSION"
            version == akkaGrpcExt.pluginVersion
        }
    }

    @Override
    String pluginLanguage() {
        "scala"
    }
}
