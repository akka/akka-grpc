scalaVersion := "2.13.12"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

//#setup
import scalapb.GeneratorOption._

enablePlugins(AkkaGrpcPlugin)

libraryDependencies +=
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
Compile / PB.targets +=
  scalapb.validate.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
//#setup
