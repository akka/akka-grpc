resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions")

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
