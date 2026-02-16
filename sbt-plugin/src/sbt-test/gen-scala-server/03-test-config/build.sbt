scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))
enablePlugins(AkkaGrpcPlugin)

Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)

//#test
Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client)
Test / PB.protoSources ++= (Compile / PB.protoSources).value
//#test

//#it
configs(IntegrationTest)
Defaults.itSettings
AkkaGrpcPlugin.configSettings(IntegrationTest)

IntegrationTest / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
IntegrationTest / PB.protoSources ++= (Compile / PB.protoSources).value
//#it
