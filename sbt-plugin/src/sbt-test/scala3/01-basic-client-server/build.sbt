scalaVersion := "3.3.1"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  // just to make sure it works with Scala 3 artifacts
  "com.typesafe.akka" %% "akka-http" % "10.7.3",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test")
