/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.testkit.TestKit
import akka.util.ByteString
import io.grpc.{ Metadata, Status, StatusRuntimeException }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

class AkkaHttpClientUtilsSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val patience: PatienceConfig =
    PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  "The conversion from HttpResponse to Source" should {
    "map a strict 404 response to a failed stream" in {
      val requestUri = Uri("https://example.com/GuestExeSample/GrpcHello")
      val response =
        Future.successful(HttpResponse(NotFound, entity = Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = AkkaHttpClientUtils.responseToSource(requestUri, response, null, false)

      val failure = source.run().failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      // https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.UNIMPLEMENTED)
    }

    "map a strict 200 response with non-0 gRPC error code to a failed stream" in {
      val requestUri = Uri("https://example.com/GuestExeSample/GrpcHello")
      val responseHeaders = RawHeader("grpc-status", "9") ::
        RawHeader("custom-key", "custom-value-in-header") ::
        RawHeader("custom-key-bin", ByteString("custom-trailer-value").encodeBase64.utf8String) ::
        Nil
      val response =
        Future.successful(HttpResponse(OK, responseHeaders, Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = AkkaHttpClientUtils.responseToSource(requestUri, response, null, false)

      val failure = source.run().failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(key) should be("custom-value-in-header")
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(keyBin) should be(ByteString("custom-trailer-value"))
    }

    "map a strict 200 response with non-0 gRPC error code with a trailer to a failed stream with trailer metadata" in {
      val requestUri = Uri("https://example.com/GuestExeSample/GrpcHello")
      val responseHeaders = List(RawHeader("grpc-status", "9"))
      val responseTrailers = Trailer(
        RawHeader("custom-key", "custom-trailer-value") ::
        RawHeader("custom-key-bin", ByteString("custom-trailer-value").encodeBase64.utf8String) ::
        Nil)
      val response = Future.successful(
        new HttpResponse(
          OK,
          responseHeaders,
          Map.empty[AttributeKey[_], Any].updated(AttributeKeys.trailer, responseTrailers),
          Strict(GrpcProtocolNative.contentType, ByteString.empty),
          HttpProtocols.`HTTP/1.1`))
      val source = AkkaHttpClientUtils.responseToSource(requestUri, response, null, false)

      val failure = source.run().failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
      failure.asInstanceOf[StatusRuntimeException].getTrailers should not be null
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(key) should be("custom-trailer-value")
      failure.asInstanceOf[StatusRuntimeException].getTrailers.get(keyBin) should be(ByteString("custom-trailer-value"))
    }

    lazy val key = Metadata.Key.of("custom-key", Metadata.ASCII_STRING_MARSHALLER)
    lazy val keyBin = Metadata.Key.of("custom-key-bin", Metadata.BINARY_BYTE_MARSHALLER)
  }
}
