scalaVersion := "2.13.17"

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

// Not likely to be used in sbt projects but easiest to test here
akkaGrpcCodeGeneratorSettings += "generate_scala_handler_factory"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
