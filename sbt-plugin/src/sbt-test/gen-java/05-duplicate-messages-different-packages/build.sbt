scalaVersion := "2.13.13"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
