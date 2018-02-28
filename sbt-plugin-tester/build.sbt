enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"

val root = project.in(file("."))
  .dependsOn(ProjectRef(file("../"), "akka-grpc-server"))
