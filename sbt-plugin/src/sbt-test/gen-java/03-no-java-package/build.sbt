scalaVersion := "2.13.17"

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
