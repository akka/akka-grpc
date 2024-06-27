enablePlugins(BuildInfoPlugin)

val sbtProtocV = "1.0.7"

buildInfoKeys := Seq[BuildInfoKey]("sbtProtocVersion" -> sbtProtocV)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % sbtProtocV)
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.6")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.3")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")

// docs
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.57")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.3")
addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % "1.7.0")

// For RawText
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.3.202401111512-r"

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.15"
