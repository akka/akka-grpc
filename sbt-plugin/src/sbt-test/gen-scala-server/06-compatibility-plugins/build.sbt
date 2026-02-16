scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

// Disable for now because of: https://github.com/protocolbuffers/protobuf-javascript/issues/127
// enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(AkkaGrpcPlugin)
