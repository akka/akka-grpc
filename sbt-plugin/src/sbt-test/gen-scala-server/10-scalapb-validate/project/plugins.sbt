resolvers += "Akka library repository".at("https://repo.akka.io/maven/github_actions")

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.0")
