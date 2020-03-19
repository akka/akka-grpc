/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.ProtobufSerialization
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpEntity.{ Chunked, LastChunk }
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import example.myapp.helloworld.grpc.{ GreeterService, GreeterServiceHandlerFactory }
import io.grpc.Status
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class ErrorReportingSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  implicit val format = ProtobufSerialization.Protobuf
  override implicit val patienceConfig = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  "A gRPC server" should {
    implicit val mat = ActorMaterializer()

    val handler = GreeterServiceHandlerFactory.create(new GreeterServiceImpl(mat), mat, sys)
    val binding = {
      import akka.http.javadsl.{ ConnectHttp, Http }

      Http(sys).bindAndHandleAsync(handler, ConnectHttp.toHost("127.0.0.1", 0), mat).toCompletableFuture.get
    }

    "respond with an 'unimplemented' gRPC error status when calling an unknown method" in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        entity = HttpEntity.empty(GrpcProtocolNative.contentType),
        uri = s"http://localhost:${binding.localAddress.getPort}/${GreeterService.name}/UnknownMethod")
      val response = Http().singleRequest(request).futureValue

      response.status should be(StatusCodes.OK)
      allHeaders(response) should contain(RawHeader("grpc-status", Status.Code.UNIMPLEMENTED.value().toString))
    }

    "respond with an 'invalid argument' gRPC error status when calling an method without a request body" in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        entity = HttpEntity.empty(GrpcProtocolNative.contentType),
        uri = s"http://localhost:${binding.localAddress.getPort}/${GreeterService.name}/SayHello")
      val response = Http().singleRequest(request).futureValue

      response.status should be(StatusCodes.OK)
      allHeaders(response) should contain(RawHeader("grpc-status", Status.Code.INVALID_ARGUMENT.value().toString))
    }

    def allHeaders(response: HttpResponse) =
      response.entity match {
        case Chunked(_, chunks) =>
          chunks.runWith(Sink.last).futureValue match {
            case LastChunk(_, trailingHeaders) => response.headers ++ trailingHeaders
            case _                             => response.headers
          }
        case _ =>
          response.headers
      }
  }

  override def afterAll: Unit =
    Await.result(sys.terminate(), 5.seconds)
}
