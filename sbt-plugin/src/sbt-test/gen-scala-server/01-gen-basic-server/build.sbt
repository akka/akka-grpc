PB.targets in Compile := Seq(
  // generate plain scalapb:
  scalapb.gen() -> (sourceManaged in Compile).value,
//
//  // generate akka-grpc:
//  akka.grpc.sbt.AkkaGrpcSbtCodeGenerator → (sourceManaged in Compile).value
)


libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.5.1"


// ---------- test checks --------------


TaskKey[Unit]("check") := {
  import scala.sys.process._
  "tree . ".!
}
