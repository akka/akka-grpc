addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")

// FIXME hmm, we only use it to get the "right" version of the netty thingy nowadays (in this top level project)
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.6"
