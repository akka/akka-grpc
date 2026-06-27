resolvers += "Akka library repository".at("REPLACE_WITH_REPO_URL_FROM_https://account.akka.io/token")

lazy val akkaGrpcVersion = sys.props.getOrElse("akka-grpc.version", "2.5.10")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % akkaGrpcVersion)
