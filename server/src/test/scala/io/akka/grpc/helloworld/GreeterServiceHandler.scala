

package io.akka.grpc.helloworld

import scala.concurrent.{ ExecutionContext, Future }

import akka.http.grpc.{ GrpcMarshalling, ScalapbProtobufSerializer }

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment

import akka.stream.Materializer

object GreeterServiceHandler {
  def apply(implementation: GreeterService)(implicit mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    implicit val ec: ExecutionContext = mat.executionContext

    val HelloRequestSerializer = new ScalapbProtobufSerializer(_root_.io.akka.grpc.helloworld.HelloRequest.messageCompanion)

    val HelloReplySerializer = new ScalapbProtobufSerializer(_root_.io.akka.grpc.helloworld.HelloReply.messageCompanion)

    def handle(request: HttpRequest, method: String): Future[HttpResponse] = method match {

      case "SayHello" =>
        GrpcMarshalling.unmarshal(request, HelloRequestSerializer, mat)
          .flatMap(implementation.sayHello(_))
          .map(e => GrpcMarshalling.marshal(e, HelloReplySerializer, mat))

      case "ItKeepsTalking" =>
        GrpcMarshalling.unmarshalStream(request, HelloRequestSerializer, mat)
          .flatMap(implementation.itKeepsTalking(_))
          .map(e => GrpcMarshalling.marshal(e, HelloReplySerializer, mat))

      case "StreamHellos" =>
        GrpcMarshalling.unmarshalStream(request, HelloRequestSerializer, mat)
          .flatMap(implementation.streamHellos(_))
          .map(e => GrpcMarshalling.marshal(e, HelloReplySerializer, mat))

      case _ =>
        Future.successful(HttpResponse(StatusCodes.NotFound))
    }

    Function.unlift((req: HttpRequest) => req.uri.path match {
      case Path.Slash(Segment(GreeterService.name, Path.Slash(Segment(method, Path.Empty)))) ⇒ Some(handle(req, method))
      case _ => None
    })
  }
}