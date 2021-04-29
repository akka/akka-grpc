// Can be removed when we move to 2.12.14
// https://github.com/akka/akka-grpc/pull/1279
scalaVersion := "2.12.11"

resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"
