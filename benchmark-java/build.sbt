scalaVersion := "2.13.15"

enablePlugins(AkkaGrpcPlugin)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

run / javaOptions ++= List("-Xms1g", "-Xmx1g", "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps")

// generate both client and server (default) in Java
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

val grpcVersion = "1.68.1" // checked synced by VersionSyncCheckPlugin

val runtimeProject = ProjectRef(file("../"), "akka-grpc-runtime")

val codeGenProject = ProjectRef(file("../"), "akka-grpc-codegen")

val root = project
  .in(file("."))
  .dependsOn(runtimeProject)
  // Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
  /*
  .settings(libraryDependencies ++= Seq(
    "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % "0.1+32-fd597fcb+20180618-1248"
  ))
   */
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-testing" % grpcVersion,
      "org.hdrhistogram" % "HdrHistogram" % "2.1.12",
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.12" % "test",
      "org.scalatestplus" %% "junit-4-12" % "3.2.2.0" % "test"),
    PB.artifactResolver := PB.artifactResolver.dependsOn(codeGenProject / Compile / publishLocal).value)

compile / javacOptions += "-Xlint:deprecation"
