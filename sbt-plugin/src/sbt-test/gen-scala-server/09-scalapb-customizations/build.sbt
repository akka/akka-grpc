resolvers += Resolver.sonatypeRepo("staging")
resolvers += Resolver.bintrayRepo("akka", "snapshots")

enablePlugins(AkkaGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
akkaGrpcCodeGeneratorSettings -= "flat_package"