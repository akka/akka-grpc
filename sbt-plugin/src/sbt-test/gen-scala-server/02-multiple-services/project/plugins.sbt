resolvers += "Scripted Resolver".at(sys.props("scripted.resolver"))
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))
