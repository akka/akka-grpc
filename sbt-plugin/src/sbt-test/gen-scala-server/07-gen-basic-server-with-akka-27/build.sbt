scalaVersion := "2.13.14"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

enablePlugins(AkkaGrpcPlugin)

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.10.0-M1"

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)    => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
