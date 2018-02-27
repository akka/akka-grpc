addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

lazy val plugins = project in file(".") dependsOn ProjectRef(file("../../"), "akka-grpc-sbt-plugin")
