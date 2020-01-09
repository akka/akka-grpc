/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.grpc.scaladsl.headers.`Status`
import akka.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpecLike

import akka.testkit.TestKit

import io.grpc.testing.integration.test.TestService

class GrpcExceptionHandlerSpec
    extends TestKit(ActorSystem("GrpcExceptionHandlerSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  "The default ExceptionHandler" should {
    "produce an INVALID_ARGUMENT error when the expected parameter is not found" in {
      implicit val serializer = TestService.Serializers.SimpleRequestSerializer
      val unmarshallableRequest = HttpRequest()

      val result: Future[HttpResponse] = GrpcMarshalling
        .unmarshal(unmarshallableRequest)
        .map(_ => HttpResponse())
        .recoverWith(GrpcExceptionHandler.default)

      result.futureValue.entity match {
        case Chunked(contentType, chunks) =>
          chunks.runWith(Sink.seq).futureValue match {
            case Seq(LastChunk("", List(`Status`("3")))) => // ok
          }
      }
    }
  }
}
