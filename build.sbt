import akka.grpc.Dependencies

scalaVersion := "2.12.4"

val commonSettings = Seq(
  organization := "com.lightbend.akka.grpc",

  crossSbtVersions := Vector("1.1.1"),

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  ),

)

val server = Project(
  id = "akka-grpc-server",
  base = file("server")
).settings(Dependencies.server)
  .settings(commonSettings)
