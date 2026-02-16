scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
