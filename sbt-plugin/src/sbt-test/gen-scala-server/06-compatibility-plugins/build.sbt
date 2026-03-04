scalaVersion := "2.13.17"

resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))

// Disable for now because of: https://github.com/protocolbuffers/protobuf-javascript/issues/127
// enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(AkkaGrpcPlugin)
