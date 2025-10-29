resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val akkaGrpcVersion = sys.props.getOrElse("akka-grpc.version", "2.5.8")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % akkaGrpcVersion)
