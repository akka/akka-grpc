resolvers += "Akka library repository".at("REPLACE_WITH_REPO_URL_FROM_https://account.akka.io/token")

addSbtPlugin(
  "com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.5.10"
)
