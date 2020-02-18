/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.grpc.scaladsl.headers.`Status`
import akka.grpc.internal.GrpcEntityHelpers
import akka.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }

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
        case other =>
          fail(s"Unexpected [$other]")
      }
    }

    import example.myapp.helloworld.grpc.helloworld._
    object ExampleImpl extends GreeterService {

      import akka.NotUsed
      import akka.stream.scaladsl.Source
      //#unary
      import akka.grpc.GrpcServiceException

      import io.grpc.Status

      def sayHello(in: HelloRequest): Future[HelloReply] = {
        if (in.name.isEmpty)
          Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found")))
        else
          Future.successful(HelloReply(s"Hi ${in.name}!"))
      }
      //#unary
      lazy val myResponseSource: Source[HelloReply, NotUsed] = ???
      //#streaming
      def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
        if (in.name.isEmpty)
          Source.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found")))
        else
          myResponseSource
      }
      //#streaming

      def itKeepsTalking(
          in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
          : scala.concurrent.Future[example.myapp.helloworld.grpc.helloworld.HelloReply] = ???
      def streamHellos(
          in: akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloRequest, akka.NotUsed])
          : akka.stream.scaladsl.Source[example.myapp.helloworld.grpc.helloworld.HelloReply, akka.NotUsed] = ???

    }

    "return the correct user-supplied status for a unary call" in {
      import akka.http.scaladsl.client.RequestBuilding._
      implicit val serializer =
        example.myapp.helloworld.grpc.helloworld.GreeterService.Serializers.HelloRequestSerializer
      implicit val codec = akka.grpc.Identity

      val request = Get(s"/${GreeterService.name}/SayHello", GrpcEntityHelpers(HelloRequest("")))

      val reply = GreeterServiceHandler(ExampleImpl).apply(request).futureValue

      val lastChunk = reply.entity.asInstanceOf[Chunked].chunks.runWith(Sink.last).futureValue.asInstanceOf[LastChunk]
      // Invalid argument is '3' https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
      val statusHeader = lastChunk.trailer.find { _.name == "grpc-status" }
      statusHeader.map(_.value()) should be(Some("3"))
      val statusMessageHeader = lastChunk.trailer.find { _.name == "grpc-message" }
      statusMessageHeader.map(_.value()) should be(Some("No name found"))
    }
  }
}
