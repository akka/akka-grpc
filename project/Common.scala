import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Common extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = JvmPlugin

  override def globalSettings = Seq(
    organization := "com.lightbend.akka.grpc",
    organizationName := "Lightbend Inc.",
    organizationHomepage := Some(url("https://www.lightbend.com/")),
    //    apiURL := Some(url(s"https://doc.akka.io/api/akka-grpc/${version.value}")),
    homepage := Some(url("https://akka.io/")),
    scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-grpc"), "git@github.com:akka/akka-grpc")),
    developers += Developer("contributors",
      "Contributors",
      "https://gitter.im/akka/dev",
      url("https://github.com/akka/akka-grpc/graphs/contributors")),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    description := "Akka gRPC - Support for building streaming gRPC servers and clients on top of Akka Streams."
  )

  override lazy val projectSettings = Seq(
    crossVersion := CrossVersion.binary,
    scalacOptions ++= List(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-encoding", "UTF-8"
    ),
    javacOptions ++= List(
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    )
  ) ++ akka.grpc.Formatting.formatSettings
}
