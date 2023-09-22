scalaVersion := "2.13.11"

resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
