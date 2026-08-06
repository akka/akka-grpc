resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

//#plugin-setup
libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.6")

// scalapb-validate-codegen 0.3.6 depends on compilerplugin 0.11.x; allow eviction to 1.0.x
libraryDependencySchemes += "com.thesamet.scalapb" %% "compilerplugin" % "always"
//#plugin-setup
