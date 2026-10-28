scalaVersion := "2.13.17"

enablePlugins(AkkaGrpcPlugin)

javacOptions += "-Xdoclint:all"

akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % akka.grpc.gen.BuildInfo.googleProtobufVersion % "protobuf"

// Client: wildcard include for "F*" services, then exclude FourthService
// Result: client generated only for FirstService
akkaGrpcClientInclude := Seq("filter.F*")
akkaGrpcClientExclude := Seq("filter.FourthService")

// Server: wildcard include for all services, then exclude Internal* and SecondService
// Result: server generated for FirstService, ThirdService, FourthService
akkaGrpcServerInclude := Seq("filter.*Service")
akkaGrpcServerExclude := Seq("filter.Internal*", "filter.SecondService")
