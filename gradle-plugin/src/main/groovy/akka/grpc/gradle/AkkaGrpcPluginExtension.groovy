package akka.grpc.gradle


import org.gradle.api.Project

class AkkaGrpcPluginExtension {

    static final String PLUGIN_CODE = 'com.lightbend.akka.grpc.gradle'

    static final String PROTOC_VERSION = "3.4.0"

    static final String PROTOC_PLUGIN_SCALA_VERSION = "2.12"

    static final String GRPC_VERSION = "1.30.0"

    // workaround for tests, where there's no jar and MANIFEST.MF can't be read
    final String pluginVersion = System.getProperty("akkaGrpcTest.pluginVersion", AkkaGrpcPlugin.class.package.implementationVersion)

    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    List<String> extraGenerators = []

    private final Project project

    final boolean scala

    AkkaGrpcPluginExtension(Project project) {
        this.project = project
        def scalaFiles = project.fileTree("src/main").matching { include "**/*.scala" }
        if (!scalaFiles.isEmpty()) {
            project.logger.info("Detected ${scalaFiles.size()} Scala source files. Plugin works in `scala` mode.")
            project.logger.debug("Scala files ${scalaFiles.files}")
        } else {
            project.logger.info("No Scala source files detected. Plugin works in `java` mode.")
        }
        this.scala = !scalaFiles.isEmpty()
    }
}
