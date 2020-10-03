resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

javacOptions  += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

PB.recompile / PB.protocOptions ++= "include_std_types"