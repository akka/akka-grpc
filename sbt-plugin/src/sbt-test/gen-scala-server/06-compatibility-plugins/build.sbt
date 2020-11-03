resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(AkkaGrpcPlugin)
