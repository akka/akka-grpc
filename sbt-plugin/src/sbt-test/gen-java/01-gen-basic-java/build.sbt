resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.15.6" % "protobuf"
