// #java-agent-plugin
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
// #java-agent-plugin

lazy val plugins = project in file(".") dependsOn ProjectRef(file("../../"), "sbt-akka-grpc")
