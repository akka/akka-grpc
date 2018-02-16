addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.6"
