package akka.grpc.gradle

import org.gradle.api.Project

class AkkaGrpcPluginExtension {

    static final String DEFAULT_SCALA_VERSION = "2.12"

    // hack for tests, since manifest is not accesible during tests run
    final String pluginVersion = System.getProperty("akkaGrpc.version", AkkaGrpcPlugin.class.package.implementationVersion)

    String language
    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    List<String> extraGenerators = []

    AkkaGrpcPluginExtension(Project project) {
        if (project.plugins.hasPlugin("scala")) {
            language = "Scala"
        } else {
            language = "Java"
        }
    }
}
