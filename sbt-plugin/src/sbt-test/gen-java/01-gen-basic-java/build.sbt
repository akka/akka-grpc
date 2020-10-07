resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

javacOptions  += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

Compile / PB.protocOptions += "--include_std_types"
