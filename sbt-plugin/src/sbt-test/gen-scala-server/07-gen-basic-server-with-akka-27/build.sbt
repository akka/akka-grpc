scalaVersion := "2.13.12"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

enablePlugins(AkkaGrpcPlugin)

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.9.0"

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)    => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
