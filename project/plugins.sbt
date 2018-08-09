addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.13")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.8.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.9")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.3.6")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.12")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

// FIXME hmm, we only use it to get the "right" version of the netty thingy nowadays (in this top level project)
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.7.1"

// #java-agent-plugin
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
// #java-agent-plugin