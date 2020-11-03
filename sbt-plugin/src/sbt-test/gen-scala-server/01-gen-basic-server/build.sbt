resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)
