scalaVersion := "2.13.17"

resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))

enablePlugins(AkkaGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
akkaGrpcCodeGeneratorSettings -= "flat_package"
