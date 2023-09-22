scalaVersion := "2.13.11"

resolvers += Resolver.sonatypeRepo("staging")

//#setup
import scalapb.GeneratorOption._

enablePlugins(AkkaGrpcPlugin)

libraryDependencies +=
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
Compile / PB.targets +=
  scalapb.validate.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
//#setup
