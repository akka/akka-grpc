resolvers += "Akka library repository".at("https://repo.akka.io/maven")
lazy val plugins = project in file(".") dependsOn ProjectRef(file("../../"), "sbt-akka-grpc")
// Use this instead of above when importing to IDEA, after publishLocal and replacing the version here
//addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "0.1+32-fd597fcb+20180618-1248")

