scalaVersion := "3.2.2"

resolvers += Resolver.sonatypeRepo("staging")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  // just to make sure it works with Scala 3 artifacts
  "com.typesafe.akka" %% "akka-http" % "10.5.0",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test")
