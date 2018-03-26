/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.grpc.interop

import akka.NotUsed
import akka.grpc.scaladsl.GrpcMarshalling
import akka.grpc.{ GrpcResponse, Identity }
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import io.grpc.testing.integration.test.{ AkkaGrpcClientTester, TestServiceHandler }
import io.grpc.testing.integration.TestServiceHandlerFactory
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{ FromRequestUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.grpc.testing.integration.messages.{ SimpleRequest, SimpleResponse, StreamingOutputCallRequest, StreamingOutputCallResponse }
import io.grpc.testing.integration.test.TestService
import org.scalatest._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Promise }

class GrpcInteropSpec extends WordSpec with GrpcInteropTests with Directives {

  grpcTests(IoGrpcJavaServerProvider, IoGrpcJavaClientProvider)
  grpcTests(IoGrpcJavaServerProvider, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderScala, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderScala, AkkaHttpClientProvider)

  grpcTests(AkkaHttpServerProviderJava, IoGrpcJavaClientProvider)
  grpcTests(AkkaHttpServerProviderJava, AkkaHttpClientProvider)

  object AkkaHttpServerProviderJava extends AkkaHttpServerProvider {
    val label: String = "akka-grpc server java"

    val pendingCases =
      Set(
        "custom_metadata",
        "client_compressed_unary",
        "client_compressed_streaming")

    val server = new AkkaGrpcServerJava(mat ⇒ {
      TestServiceHandlerFactory.create(new JavaTestServiceImpl(mat), mat)
    })
  }

  object AkkaHttpClientProvider extends AkkaHttpClientProvider {
    def client = AkkaGrpcClientScala(settings => implicit mat => implicit ec => new AkkaGrpcClientTester(settings))
  }

}
