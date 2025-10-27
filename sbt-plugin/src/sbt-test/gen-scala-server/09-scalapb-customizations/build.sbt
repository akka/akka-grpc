scalaVersion := "2.13.17"

resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions")

enablePlugins(AkkaGrpcPlugin)

// Don't enable it flat_package globally, but via a package-level option instead (see package.proto)
akkaGrpcCodeGeneratorSettings -= "flat_package"
