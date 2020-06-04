resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.10" % "runtime"

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.6.5"

// Should no longer be needed from Akka 2.6.6, which includes
// d2afff6bfc29baf0fb03f531fbe3af40a97fd39f
assemblyMergeStrategy in assembly := {
  case PathList("google", "protobuf", "field_mask.proto") =>
    // The only difference is in a comment, so we could also just pick
    // one, but we don't need it anyway so better to be deterministic:
    MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}