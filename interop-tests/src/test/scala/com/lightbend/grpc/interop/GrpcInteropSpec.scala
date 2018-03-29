/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

import akka.NotUsed
import akka.grpc.scaladsl.GrpcMarshalling
import akka.grpc.{ GrpcResponse, Identity }
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import io.grpc.testing.integration.test.{ AkkaGrpcScalaClientTester, TestService, TestServiceHandler }
import io.grpc.testing.integration.{ AkkaGrpcJavaClientTester, TestServiceHandlerFactory }
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages._
import org.scalatest._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderScala)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProviderJava)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderScala)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProviderJava)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderScala)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProviderJava)

  object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
    val label: String = "akka-grpc java server"

    val pendingCases =
      Set(
        "custom_metadata",
        "client_compressed_unary",
        "client_compressed_streaming")

    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProviderScala extends AkkaHttpClientProvider {
    val label: String = "akka-grpc scala client tester"

    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcScalaClientTester(settings))
  }

  object AkkaHttpClientProviderJava extends AkkaHttpClientProvider {
    val label: String = "akka-grpc java client tester"

    def client = new AkkaGrpcClientJava((settings, mat, ec) => new AkkaGrpcJavaClientTester(settings, mat, ec))
  }

}
