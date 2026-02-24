scalaVersion := "2.13.17"

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  // just to make sure it works with Scala 2.13 artifacts
  "com.typesafe.akka" %% "akka-http" % "10.7.3",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test")
