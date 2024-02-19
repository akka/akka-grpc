resolvers += "Akka library repository".at("https://repo.akka.io/maven")

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.0")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")
