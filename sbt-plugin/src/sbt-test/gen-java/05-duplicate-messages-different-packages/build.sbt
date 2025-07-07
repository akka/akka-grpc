scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions/github_actions")

enablePlugins(AkkaGrpcPlugin)

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
