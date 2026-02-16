scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

enablePlugins(AkkaGrpcPlugin)
