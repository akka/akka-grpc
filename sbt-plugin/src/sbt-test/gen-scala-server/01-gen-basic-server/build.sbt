scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

assembly / assemblyMergeStrategy := {
  // https://github.com/akka/akka/issues/29456
  case PathList("google", "protobuf", _)       => MergeStrategy.discard
  case PathList("google", "protobuf", _, _)    => MergeStrategy.discard
  case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
