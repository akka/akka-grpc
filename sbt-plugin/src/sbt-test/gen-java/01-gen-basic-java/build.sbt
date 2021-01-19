resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % "1.18.1-0" % "protobuf"
