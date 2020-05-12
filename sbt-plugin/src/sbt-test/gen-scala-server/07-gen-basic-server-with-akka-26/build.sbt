resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"

dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % "2.6.4"
