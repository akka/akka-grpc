scalaVersion := "2.13.17"

resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
