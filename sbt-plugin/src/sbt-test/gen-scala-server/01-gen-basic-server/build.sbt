resolvers += Resolver.sonatypeRepo("staging")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)