resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.0")
