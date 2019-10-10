enablePlugins(AkkaGrpcPlugin)

Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)

Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client)

configs(IntegrationTest)
Defaults.itSettings
AkkaGrpcPlugin.configSettings(IntegrationTest)
IntegrationTest / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)
