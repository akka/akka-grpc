// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.17"

resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.7.0-M5"

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)    => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
