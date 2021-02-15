addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % sys.props("project.version"))

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.2.1")
