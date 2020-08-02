enablePlugins(BuildInfoPlugin)

val sbtProtocV = "0.99.34"

buildInfoKeys := Seq[BuildInfoKey]("sbtProtocVersion" -> sbtProtocV)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % sbtProtocV)
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.18")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.7.0")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.24")

// docs
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.34")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.0")

// For RawText
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "5.8.1.202007141445-r"

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.8"
