scalaVersion := "2.13.17"

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client)
akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

// Not likely to be used in sbt projects - meant for Akka SDK Java/maven projects - but easiest to test here
akkaGrpcCodeGeneratorSettings += "generate_scala_handler_factory"
akkaGrpcCodeGeneratorSettings += "blocking_apis"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
