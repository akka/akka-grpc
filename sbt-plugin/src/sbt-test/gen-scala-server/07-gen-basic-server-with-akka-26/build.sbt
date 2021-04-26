resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.6.5"

// Should no longer be needed from Akka 2.6.6, which includes
// d2afff6bfc29baf0fb03f531fbe3af40a97fd39f
assembly / assemblyMergeStrategy := {
  case PathList("google", "protobuf", "field_mask.proto") =>
    // The only difference is in a comment, so we could also just pick
    // one, but we don't need it anyway so better to be deterministic:
    MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
