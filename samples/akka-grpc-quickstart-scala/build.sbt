name := "akka-grpc-quickstart-scala"

version := "1.0"
scalaVersion := "2.13.15"

val akkaVersion = "2.10.0"
lazy val akkaGrpcVersion = sys.props.getOrElse("akka-grpc.version", "2.5.0")

enablePlugins(AkkaGrpcPlugin)

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-pki" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.15",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.12" % Test)
