package akka.grpc

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import akka.grpc.Dependencies.Versions.{ scala212, scala213 }
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys.projectInfoVersion
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import com.typesafe.tools.mima.plugin.MimaKeys._
import sbtprotoc.ProtocPlugin.autoImport.PB
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object Common extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = JvmPlugin && Sonatype

  private val consoleDisabledOptions = Seq("-Xfatal-warnings", "-Ywarn-unused", "-Ywarn-unused-import")

  override def globalSettings =
    Seq(
      commands += switchVersion,
      organization := "com.lightbend.akka.grpc",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://www.lightbend.com/")),
      resolvers += Resolver.sonatypeRepo("staging"),
      homepage := Some(url("https://akka.io/")),
      scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-grpc"), "git@github.com:akka/akka-grpc")),
      developers += Developer(
        "contributors",
        "Contributors",
        "https://gitter.im/akka/dev",
        url("https://github.com/akka/akka-grpc/graphs/contributors")),
      licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
      description := "Akka gRPC - Support for building streaming gRPC servers and clients on top of Akka Streams.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    sonatypeProfileName := "com.lightbend",
    scalacOptions ++= List(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-Xfatal-warnings",
      "-Ywarn-unused",
      "-encoding",
      "UTF-8"),
    Compile / scalacOptions ++= Seq(
      // Generated code for methods/fields marked 'deprecated'
      "-Wconf:msg=Marked as deprecated in proto file:silent",
      // deprecated in 2.13, but used as long as we support 2.12
      "-Wconf:msg=Use `scala.jdk.CollectionConverters` instead:silent",
      "-Wconf:msg=Use LazyList instead of Stream:silent",
      // ignore imports in templates (FIXME why is that trailig .* needed?)
      "-Wconf:src=.*.txt.*:silent"),
    Compile / console / scalacOptions ~= (_.filterNot(consoleDisabledOptions.contains)),
    javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation"),
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
      "-doc-title",
      "Akka gRPC",
      "-doc-version",
      version.value,
      "-sourcepath",
      (ThisBuild / baseDirectory).value.toString,
      "-skip-packages",
      "akka.pattern:" + // for some reason Scaladoc creates this
      "templates",
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "main" else s"v${version.value}"
        s"https://github.com/akka/akka-grpc/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
      },
      "-doc-canonical-base-url",
      "https://doc.akka.io/api/akka-grpc/current/"),
    Compile / doc / scalacOptions -= "-Xfatal-warnings",
    apiURL := Some(url(s"https://doc.akka.io/api/akka-grpc/${projectInfoVersion.value}/akka/grpc/index.html")),
    (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    crossScalaVersions := Seq(scala212, scala213),
    mimaReportSignatureProblems := true,
    scalafmtOnCompile := true)

  // So we can `sbt "+~ 3 clean compile"`
  //
  // The advantage over `++` is twofold:
  // * `++` also requires the patch version, `+~` finds the first supported Scala version that matches the prefix (if any)
  // * When subprojects need to be excluded, ++ needs to be specified for each command
  //
  // So the `++` equivalent of the above example is `sbt "++ 3.1.1-RC1 clean" "++ 3.1.1-RC1 compile"`
  val switchVersion: Command = Command.args("+~", "<version> <args>") { (initialState: State, args: Seq[String]) =>
    {
      val requestedVersionPrefix = args.head
      val requestedVersion = Dependencies.Versions.CrossScalaForLib.filter(_.startsWith(requestedVersionPrefix)).head

      def run(state: State, command: String): State = {
        val parsed = s"++ $requestedVersion $command".foldLeft(Cross.switchVersion.parser(state))((p, i) => p.derive(i))
        parsed.resultEmpty match {
          case e: sbt.internal.util.complete.Parser.Failure =>
            throw new IllegalStateException(e.errors.mkString(", "))
          case sbt.internal.util.complete.Parser.Value(v) =>
            v()
        }
      }
      val commands = args.tail
      commands.foldLeft(initialState)(run)
    }
  }
}
