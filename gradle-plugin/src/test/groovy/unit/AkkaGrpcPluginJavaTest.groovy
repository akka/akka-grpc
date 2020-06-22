package unit

import akka.grpc.gradle.AkkaGrpcPluginExtension
import helper.BaseUnitTest
import org.gradle.api.artifacts.Dependency

import static akka.grpc.gradle.AkkaGrpcPluginExtension.getDEFAULT_SCALA_VERSION
import static akka.grpc.gradle.AkkaGrpcPluginExtension.getDEFAULT_SCALA_VERSION

class AkkaGrpcPluginJavaTest extends BaseUnitTest {

    def "should create akkaGrpc extension with default values"() {
        expect:
        with(akkaGrpcExt) {
            it instanceof AkkaGrpcPluginExtension
            it.pluginVersion == System.getProperty("akkaGrpc.version")
            it.scalaVersion == DEFAULT_SCALA_VERSION
            it.language.equalsIgnoreCase("java")
        }
    }

    def "should add protobuf plugins with default pluginVersion and scalaVersion"() {
        when:
        project.evaluate()
        then:
        Map<String, Dependency> plugins = project.protobuf.tools.plugins.collectEntries { [(it.name): project.dependencies.create(it.artifact)] }
        plugins.keySet().sort() == ["akkaGrpc"]

        with(plugins.akkaGrpc) {
            group == "com.lightbend.akka.grpc"
            name == "akka-grpc-codegen_$DEFAULT_SCALA_VERSION"
            version == akkaGrpcExt.pluginVersion
        }
    }

    @Override
    String pluginLanguage() {
        "java"
    }
}
