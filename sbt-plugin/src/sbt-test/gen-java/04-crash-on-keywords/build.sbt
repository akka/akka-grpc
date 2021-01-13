resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

javacOptions  += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

Compile / PB.protocOptions += "--include_std_types"
