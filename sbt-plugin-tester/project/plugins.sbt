addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % {
  val is = io.Source.fromFile(new File("../version.sbt")).mkString
  val it = is.replaceAll("version in ThisBuild := ", "").replaceAll("\"", "").replaceAll("\n", "")
  it
}, "0.13", "2.10")


addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"
