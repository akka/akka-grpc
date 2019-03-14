package akka.grpc

import sbt._, Keys._

/**
 * For projects that are not to be published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings = Seq(
    skip in publish := true,
  )
}

object Publish extends AutoPlugin {
  import bintray.BintrayPlugin
  import bintray.BintrayPlugin.autoImport._

  override def trigger = allRequirements
  override def requires = BintrayPlugin

  override def projectSettings = Seq(
    bintrayOrganization := Some("akka"),
    bintrayPackage := "akka-grpc",
    homepage := Some(url("https://developer.lightbend.com/docs/akka-grpc/current/")),
    scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-grpc"), "git@github.com:akka/akka-grpc")),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    developers += Developer("contributors",
      "Contributors",
      "https://gitter.im/akka/dev",
      url("https://github.com/akka/akka-grpc/graphs/contributors")),
  )
}

