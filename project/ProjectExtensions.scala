package akka.grpc

import sbt._
import sbt.Keys._
import com.lightbend.sbt.javaagent.JavaAgent
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents

// helper to define projects that test the plugin infrastructure
object ProjectExtensions {
  implicit class AddPluginTest(project: Project) {

    /** Add settings to test the sbt-plugin in-process */
    def pluginTestingSettings: Project =
      project
        .enablePlugins(JavaAgent)
        .settings(
          javaOptions ++= Seq("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y"),
          // #alpn
          // ALPN agent, only required on JVM 8
          javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.10"
            // #alpn
            % "test")
        .dependsOn(ProjectRef(file("."), "akka-grpc-runtime"))
        .enablePlugins(akka.grpc.build.ReflectiveCodeGen)
  }
}
