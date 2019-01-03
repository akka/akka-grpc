/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.duration._
import scala.collection.immutable
import akka.actor.ActorSystem
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.grpc.{ Grpc, Gzip }
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import io.grpc.{ Status, StatusException }
import io.grpc.testing.integration.messages.{ PayloadType, SimpleRequest }
import io.grpc.testing.integration.test.TestService
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Await
import scala.util.Failure

class GrpcMarshallingSpec extends WordSpec with Matchers {
  "The scaladsl GrpcMarshalling" should {
    val message = SimpleRequest(responseType = PayloadType.RANDOM)
    implicit val serializer = TestService.Serializers.SimpleRequestSerializer
    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    val zippedBytes = Grpc.encodeFrame(Grpc.compressed, Gzip.compress(serializer.serialize(message)))

    "correctly unmarshal a zipped object" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("gzip")),
        entity = HttpEntity.Strict(Grpc.contentType, zippedBytes))

      val marshalled = Await.result(GrpcMarshalling.unmarshal(request), 10.seconds)
      marshalled.responseType should be(PayloadType.RANDOM)
    }

    "correctly unmarshal a zipped stream" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("gzip")),
        entity = HttpEntity.Strict(Grpc.contentType, zippedBytes ++ zippedBytes))

      val stream = Await.result(GrpcMarshalling.unmarshalStream(request), 10.seconds)
      val items = Await.result(stream.runWith(Sink.seq), 10.seconds)
      items(0).responseType should be(PayloadType.RANDOM)
      items(1).responseType should be(PayloadType.RANDOM)
    }

    // https://github.com/grpc/grpc/blob/master/doc/compression.md#compression-method-asymmetry-between-peers
    // test case 6
    "fail with INTERNAL when the compressed bit is on but the encoding is identity" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("identity")),
        entity = HttpEntity.Strict(Grpc.contentType, zippedBytes))

      Await.ready(GrpcMarshalling.unmarshal(request), 10.seconds).value.get match {
        case Failure(e: StatusException) ⇒
          e.getStatus.getCode should be(Status.Code.INTERNAL)
          e.getStatus.getDescription should include("encoding")
        case _ ⇒ fail()
      }
    }

    // https://github.com/grpc/grpc/blob/master/doc/compression.md#compression-method-asymmetry-between-peers
    // test case 6
    "fail with INTERNAL when the compressed bit is on but the encoding is missing" in {
      val request = HttpRequest(
        entity = HttpEntity.Strict(Grpc.contentType, zippedBytes))

      Await.ready(GrpcMarshalling.unmarshal(request), 10.seconds).value.get match {
        case Failure(e: StatusException) ⇒
          e.getStatus.getCode should be(Status.Code.INTERNAL)
          e.getStatus.getDescription should include("encoding")
        case _ ⇒ fail()
      }
    }
  }

}
