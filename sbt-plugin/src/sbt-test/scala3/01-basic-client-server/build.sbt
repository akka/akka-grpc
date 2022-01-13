scalaVersion := "3.1.0"

resolvers += Resolver.sonatypeRepo("staging")

scalacOptions += "-Xfatal-warnings"

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.9" % "test"
)
