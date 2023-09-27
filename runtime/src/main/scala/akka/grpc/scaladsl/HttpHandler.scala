package akka.grpc.scaladsl

import akka.annotation.ApiMayChange
import akka.grpc.internal.HttpTranscoding
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import com.google.protobuf.Descriptors.FileDescriptor

import scala.concurrent.{ ExecutionContext, Future }

object HttpHandler {

  @ApiMayChange
  def partial(fileDescriptor: FileDescriptor, grpcHandler: PartialFunction[HttpRequest, Future[HttpResponse]])(
      implicit mat: Materializer,
      ec: ExecutionContext): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    val handlers = HttpTranscoding.parseRules(fileDescriptor).map {
      case (method, binding) => HttpTranscoding.httpHandler(method, binding, grpcHandler)
    }
    ServiceHandler.concat(handlers: _*)
  }

}
