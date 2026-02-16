scalaVersion := "2.13.17"

resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))

enablePlugins(AkkaGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
akkaGrpcCodeGeneratorSettings -= "flat_package"
