scalaVersion := "2.12.4"
val AkkaHttp = "10.1.0-RC2+12-7675041d"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.9",
  "com.typesafe.akka" %% "akka-http" % AkkaHttp,
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttp,

  "io.grpc" % "grpc-netty" % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion,
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"
