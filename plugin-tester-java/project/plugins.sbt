addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

lazy val plugins = project in file(".") dependsOn ProjectRef(file("../../"), "sbt-akka-grpc")

