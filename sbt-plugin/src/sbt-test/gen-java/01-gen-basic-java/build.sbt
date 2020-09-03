resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

javacOptions  += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
