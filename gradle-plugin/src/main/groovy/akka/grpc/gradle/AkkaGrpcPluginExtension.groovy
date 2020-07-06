package akka.grpc.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project

class AkkaGrpcPluginExtension {

    static final String PLUGIN_CODE = 'com.lightbend.akka.grpc.gradle'

    static final String PROTOC_VERSION = "3.4.0"

    static final String PROTOC_PLUGIN_SCALA_VERSION = "2.12"

    static final String GRPC_VERSION = "1.30.0"

    // workaround for tests, where there's no jar and MANIFES.MF can't be read
    final String pluginVersion = System.getProperty("akkaGrpcTest.pluginVersion", AkkaGrpcPlugin.class.package.implementationVersion)

    String language
    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    List<String> extraGenerators = []

    AkkaGrpcPluginExtension(Project project) {
        if (project.pluginManager.hasPlugin("scala")) {
            language = "Scala"
        } else if (project.pluginManager.hasPlugin("java")) {
            language = "Java"
        } else {
            throw new GradleException("$PLUGIN_CODE requires either `java` or `scala` plugin to be applied before the plugin.")
        }
    }
}
