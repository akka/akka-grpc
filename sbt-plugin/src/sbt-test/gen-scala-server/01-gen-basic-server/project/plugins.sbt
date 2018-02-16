addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % sys.props("project.version"))

// TODO ideally we do not want to need to add any other plugins explicitly

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
