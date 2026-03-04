resolvers ++= sys.props.get("scripted.resolver").map(resolver => "Scripted Resolver".at(resolver))
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))
