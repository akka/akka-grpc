scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions/github_actions")

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

// Not likely to be used in sbt projects but easiest to test here
akkaGrpcCodeGeneratorSettings += "generate_scala_handler_factory"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
