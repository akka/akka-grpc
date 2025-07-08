resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions")

lazy val akkaGrpcVersion = sys.props.getOrElse("akka.grpc.version", "2.5.4")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % akkaGrpcVersion)
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")
