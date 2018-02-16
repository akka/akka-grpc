addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % sys.props("project.version"))

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"

