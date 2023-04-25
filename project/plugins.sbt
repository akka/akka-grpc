enablePlugins(BuildInfoPlugin)

val sbtProtocV = "1.0.6"

buildInfoKeys := Seq[BuildInfoKey]("sbtProtocVersion" -> sbtProtocV)

addSbtPlugin("lt.dvim.authors" % "sbt-authors" % "1.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % sbtProtocV)
addSbtPlugin("com.typesafe.play" % "sbt-twirl" % "1.5.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.2")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.30")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.4")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")

// docs
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.47")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2.2")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % "1.5.0-RC2")

// For RawText
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.1.202206130422-r"

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.13"
