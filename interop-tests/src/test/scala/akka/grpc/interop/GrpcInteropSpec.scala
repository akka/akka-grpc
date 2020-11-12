/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.IoGrpc)
class GrpcInteropIoWithAkkaScalaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Scala)
class GrpcInteropIoWithAkkaJavaSpec extends GrpcInteropTests(Servers.IoGrpc, Clients.AkkaNetty.Java)

class GrpcInteropAkkaScalaWithIoSpec extends GrpcInteropTests(Servers.AkkaHttp.Scala, Clients.IoGrpc)
class GrpcInteropAkkaScalaWithAkkaScalaSpec extends GrpcInteropTests(Servers.AkkaHttp.Scala, Clients.AkkaNetty.Scala)
class GrpcInteropAkkaScalaWithAkkaJavaSpec extends GrpcInteropTests(Servers.AkkaHttp.Scala, Clients.AkkaNetty.Java)

// TODO testing against grpc-java server (problem with the path, still to be diagnosed)
class GrpcInteropAkkaHttpScalaServerWithAkkaHttpScalaClientSpec
    extends GrpcInteropTests(Servers.AkkaHttp.Scala, Clients.AkkaHttp.Scala)

class GrpcInteropAkkaJavaWithIoSpec extends GrpcInteropTests(Servers.AkkaHttp.Java, Clients.IoGrpc)
class GrpcInteropAkkaJavaWithAkkaScalaSpec extends GrpcInteropTests(Servers.AkkaHttp.Java, Clients.AkkaNetty.Scala)
class GrpcInteropAkkaJavaWithAkkaJavaSpec extends GrpcInteropTests(Servers.AkkaHttp.Java, Clients.AkkaNetty.Java)
