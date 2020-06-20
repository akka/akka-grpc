enablePlugins(AkkaGrpcPlugin)

javaOptions in run ++= List("-Xms1g", "-Xmx1g",  "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps")

// generate both client and server (default) in Java
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

val grpcVersion = "1.30.1" // checked synced by GrpcVersionSyncCheckPlugin

val runtimeProject = ProjectRef(file("../"), "akka-grpc-runtime")

val codeGenProject = ProjectRef(file("../"), "akka-grpc-codegen")

val root = project.in(file("."))
  .dependsOn(
    runtimeProject
  )
  // Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
  /*
  .settings(libraryDependencies ++= Seq(
    "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % "0.1+32-fd597fcb+20180618-1248"
  ))
  */
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-testing" % grpcVersion,
      "org.hdrhistogram" % "HdrHistogram" % "2.1.10",
      "org.apache.commons" % "commons-math3" % "3.6",
      "org.scalatest" %% "scalatest" % "3.1.2" % "test",
      "org.scalatestplus" %% "junit-4-12" % "3.1.2.0" % "test"
    ),
    Compile / PB.generate := ((Compile / PB.generate) dependsOn (
        codeGenProject / Compile / publishLocal)).value
  )

javacOptions in compile += "-Xlint:deprecation"
