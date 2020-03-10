/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.Gzip
import akka.grpc.internal.{ AbstractGrpcProtocol, GrpcProtocolNative }
import akka.grpc.scaladsl.headers.`Message-Encoding`
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import io.grpc.{ Status, StatusException }
import io.grpc.testing.integration.messages.{ BoolValue, SimpleRequest }
import io.grpc.testing.integration.test.TestService
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class GrpcMarshallingSpec extends AnyWordSpec with Matchers {
  "The scaladsl GrpcMarshalling" should {
    val message = SimpleRequest(responseCompressed = Some(BoolValue(true)))
    implicit val serializer = TestService.Serializers.SimpleRequestSerializer
    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    val awaitTimeout = 10.seconds
    val zippedBytes =
      AbstractGrpcProtocol.encodeFrameData(
        AbstractGrpcProtocol.fieldType(Gzip),
        Gzip.compress(serializer.serialize(message)))

    "correctly unmarshal a zipped object" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("gzip")),
        entity = HttpEntity.Strict(GrpcProtocolNative.contentType, zippedBytes))

      val marshalled = Await.result(GrpcMarshalling.unmarshal(request), 10.seconds)
      marshalled.responseCompressed should be(Some(BoolValue(true)))
    }

    "correctly unmarshal a zipped stream" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("gzip")),
        entity = HttpEntity.Strict(GrpcProtocolNative.contentType, zippedBytes ++ zippedBytes))

      val stream = Await.result(GrpcMarshalling.unmarshalStream(request), 10.seconds)
      val items = Await.result(stream.runWith(Sink.seq), 10.seconds)
      items(0).responseCompressed should be(Some(BoolValue(true)))
      items(1).responseCompressed should be(Some(BoolValue(true)))
    }

    // https://github.com/grpc/grpc/blob/master/doc/compression.md#compression-method-asymmetry-between-peers
    // test case 6
    "fail with INTERNAL when the compressed bit is on but the encoding is identity" in {
      val request = HttpRequest(
        headers = immutable.Seq(`Message-Encoding`("identity")),
        entity = HttpEntity.Strict(GrpcProtocolNative.contentType, zippedBytes))

      assertFailure(GrpcMarshalling.unmarshal(request), Status.Code.INTERNAL, "encoding")
    }

    // https://github.com/grpc/grpc/blob/master/doc/compression.md#compression-method-asymmetry-between-peers
    // test case 6
    "fail with INTERNAL when the compressed bit is on but the encoding is missing" in {
      val request = HttpRequest(entity = HttpEntity.Strict(GrpcProtocolNative.contentType, zippedBytes))

      assertFailure(GrpcMarshalling.unmarshal(request), Status.Code.INTERNAL, "encoding")
    }

    def assertFailure(failure: Future[_], expectedStatusCode: Status.Code, expectedMessageFragment: String): Unit = {
      val e = Await.result(failure.failed, awaitTimeout).asInstanceOf[StatusException]
      e.getStatus.getCode should be(expectedStatusCode)
      e.getStatus.getDescription should include(expectedMessageFragment)
    }
  }
}
