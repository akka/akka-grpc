enablePlugins(AkkaGrpcPlugin)

//#alpn
enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime;test"
//#alpn

lazy val akkaVersion = "2.5.13"

val root = project.in(file("."))
  .dependsOn(
    ProjectRef(file("../"), "akka-grpc-runtime"),
    ProjectRef(file("../"), "akka-grpc-codegen"),
  )
  .settings(
    libraryDependencies += "com.typesafe" %% "ssl-config-core" % "0.2.4-SNAPSHOT"
  )

javacOptions in compile += "-Xlint:deprecation"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
