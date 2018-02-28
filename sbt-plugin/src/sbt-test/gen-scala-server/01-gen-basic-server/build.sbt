//PB.targets in Compile := Seq(
//  scalapb.gen(grpc = false) -> (sourceManaged in Compile).value
//)

enablePlugins(JavaAgent)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"

TaskKey[Unit]("check") := {
  import scala.sys.process._
  "find . ".!
}
