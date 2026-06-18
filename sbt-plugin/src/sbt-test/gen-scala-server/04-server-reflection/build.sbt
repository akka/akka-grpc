scalaVersion := "2.13.17"

resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))

enablePlugins(AkkaGrpcPlugin)
