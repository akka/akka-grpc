/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

class GrpcInteropIoWithIoSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
class GrpcInteropIoWithAkkaScalaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaNettyClientProviderScala)
class GrpcInteropIoWithAkkaJavaSpec extends GrpcInteropTests(IoGrpcJavaServerProvider, AkkaNettyClientProviderJava)

class GrpcInteropAkkaScalaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
class GrpcInteropAkkaScalaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaNettyClientProviderScala)
class GrpcInteropAkkaScalaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaNettyClientProviderJava)

// TODO testing against grpc-java server (problem with the path, still to be diagnosed)
class GrpcInteropAkkaHttpScalaServerWithAkkaHttpScalaClientSpec
    extends GrpcInteropTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderScala)

class GrpcInteropAkkaJavaWithIoSpec extends GrpcInteropTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
class GrpcInteropAkkaJavaWithAkkaScalaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaNettyClientProviderScala)
class GrpcInteropAkkaJavaWithAkkaJavaSpec
    extends GrpcInteropTests(AkkaHttpServerProviderJava, AkkaNettyClientProviderJava)
