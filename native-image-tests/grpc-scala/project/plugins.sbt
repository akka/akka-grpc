resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val akkaGrpcVersion = sys.props.getOrElse("akka.grpc.version", "2.5.1")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % akkaGrpcVersion)
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")
