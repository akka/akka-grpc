scalaVersion := "2.13.18"

resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))
//#setup
import scalapb.GeneratorOption._

enablePlugins(AkkaGrpcPlugin)

// FIXME not yet available for 1.0.0.alpha
// libraryDependencies +=
//   "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"
// Compile / PB.targets +=
//  scalapb.validate.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
//#setup
