/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.RawHeader
import akka.testkit.TestKit
import akka.util.ByteString
import io.grpc.{ Status, StatusRuntimeException }
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
      val source = AkkaHttpClientUtils.responseToSource(requestUri, response, null)

      val failure = source.run().failed.futureValue
      // https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.UNIMPLEMENTED)
    }

    "map a strict 200 response with non-0 gRPC error code to a failed stream" in {
      val requestUri = Uri("https://example.com/GuestExeSample/GrpcHello")
      val response = Future.successful(
        HttpResponse(OK, List(RawHeader("grpc-status", "9")), Strict(GrpcProtocolNative.contentType, ByteString.empty)))
      val source = AkkaHttpClientUtils.responseToSource(requestUri, response, null)

      val failure = source.run().failed.futureValue
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode should be(Status.Code.FAILED_PRECONDITION)
    }
  }
}
