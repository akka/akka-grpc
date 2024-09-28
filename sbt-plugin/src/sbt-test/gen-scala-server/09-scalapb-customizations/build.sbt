scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

enablePlugins(AkkaGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
akkaGrpcCodeGeneratorSettings -= "flat_package"
