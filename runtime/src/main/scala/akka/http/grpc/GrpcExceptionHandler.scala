package akka.http.grpc

import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ ExecutionException, Future }

object GrpcExceptionHandler {
  val default: PartialFunction[Throwable, Future[HttpResponse]] = {
    case e: ExecutionException ⇒
      if (e.getCause == null) Future.failed(e)
      else handling(e.getCause)
    case other ⇒
      handling(other)
  }
  private val handling: PartialFunction[Throwable, Future[HttpResponse]] = {
    case grpcException: GrpcServiceException ⇒
      Future.successful(GrpcMarshalling.status(grpcException.status))
    case _: NotImplementedError ⇒
      Future.successful(GrpcResponse.status(Status.UNIMPLEMENTED))
    case _: UnsupportedOperationException ⇒
      Future.successful(GrpcResponse.status(Status.UNIMPLEMENTED))
    case other ⇒
      Future.failed(other)
  }
}

class GrpcServiceException(val status: Status) extends RuntimeException(status.getDescription)