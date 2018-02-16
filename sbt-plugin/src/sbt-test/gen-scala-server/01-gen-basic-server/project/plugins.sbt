// addSbtPlugin("com.lightbend" % "akka-grpc-sbt" % sys.props("project.version"))

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.5.1"
