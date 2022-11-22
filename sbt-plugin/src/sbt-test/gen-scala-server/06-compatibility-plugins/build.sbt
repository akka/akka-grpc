// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.17"

resolvers += Resolver.sonatypeRepo("staging")

// Disable for now because of: https://github.com/protocolbuffers/protobuf-javascript/issues/127
// enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(AkkaGrpcPlugin)
