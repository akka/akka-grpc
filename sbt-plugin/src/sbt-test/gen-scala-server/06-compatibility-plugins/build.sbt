resolvers += Resolver.sonatypeRepo("staging")

enablePlugins(ProtocJSPlugin) // enable it first to test possibility of getting overriden

enablePlugins(JavaAgent)
enablePlugins(AkkaGrpcPlugin)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime"
