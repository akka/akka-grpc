package akka.grpc

import sbt._
import com.lightbend.sbt.javaagent.JavaAgent
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

// helper to define projects that test the plugin infrastructure
object ProjectExtensions {
  implicit class AddPluginTest(project: Project) {
    /** Add settings to test the sbt-plugin in-process */
    def pluginTestingSettings: Project =
      project
        .enablePlugins(JavaAgent)
        .settings(
          // #alpn
          javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9"
            // #alpn
            % "test")
        .dependsOn(ProjectRef(file("."), "akka-grpc-runtime"))
        .enablePlugins(akka.grpc.build.ReflectiveCodeGen)
        // needed to be able to override the PB.generate task reliably
        .disablePlugins(ProtocPlugin)
        .settings(ProtocPlugin.projectSettings.filterNot(_.a.key.key == PB.generate.key))
  }
}
