addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.13")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.8.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.6")

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

// FIXME hmm, we only use it to get the "right" version of the netty thingy nowadays (in this top level project)
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"
