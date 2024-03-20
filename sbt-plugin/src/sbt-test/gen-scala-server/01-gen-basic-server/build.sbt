scalaVersion := "2.13.13"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)    => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
