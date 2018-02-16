resolvers += Classpaths.sbtPluginReleases
resolvers += Classpaths.typesafeReleases
resolvers += Resolver.sonatypeRepo("releases") // to more quickly obtain paradox rigth after release

// need this to resolve http://jcenter.bintray.com/org/jenkins-ci/jenkins/1.26/
// which is used by plugin "org.kohsuke" % "github-api" % "1.68"
resolvers += Resolver.jcenterRepo

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.1")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")

addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.3.2")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "4.1.0")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")

// used for @unidoc directive
libraryDependencies += "io.github.lukehutch" % "fast-classpath-scanner" % "2.12.3"

// scripted testing
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

// FIXME hmm, we only use it to get the "right" version of the netty thingy nowadays (in this top level project)
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.6"

