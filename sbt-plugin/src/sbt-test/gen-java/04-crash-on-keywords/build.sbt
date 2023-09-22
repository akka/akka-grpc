scalaVersion := "2.13.11"

resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
