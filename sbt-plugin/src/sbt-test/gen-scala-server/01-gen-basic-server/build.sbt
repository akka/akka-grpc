// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.11"

resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

assemblyMergeStrategy in assembly := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _) => MergeStrategy.discard
  case PathList("google", "protobuf", _, _) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
