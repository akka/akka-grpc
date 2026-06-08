addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.0")

// scalapb-validate-codegen 0.3.0 depends on compilerplugin 0.11.x; allow eviction to 1.0.x
libraryDependencySchemes += "com.thesamet.scalapb" %% "compilerplugin" % "always"
