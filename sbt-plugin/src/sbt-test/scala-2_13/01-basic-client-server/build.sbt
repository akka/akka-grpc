scalaVersion := "2.13.16"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  // just to make sure it works with Scala 3 artifacts
  "com.typesafe.akka" %% "akka-http" % "10.6.3",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test")
