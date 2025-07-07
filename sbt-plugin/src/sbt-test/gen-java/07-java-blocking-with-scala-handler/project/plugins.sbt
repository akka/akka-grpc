resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions/github_actions")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))
