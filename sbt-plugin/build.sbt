sbtPlugin := true

publishTo := Some(Classpaths.sbtPluginReleases)

publishMavenStyle := false

// we depend on it in our build.sbt (of the plugin) since we set keys of the sbt-protoc plugin
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12")
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"
