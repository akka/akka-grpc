scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

// Disable for now because of: https://github.com/protocolbuffers/protobuf-javascript/issues/127
// enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(AkkaGrpcPlugin)
