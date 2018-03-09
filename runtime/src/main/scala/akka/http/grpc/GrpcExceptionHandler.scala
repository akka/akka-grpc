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
    case _: NotImplementedError ⇒
      Future.successful(GrpcMarshalling.status(Status.UNIMPLEMENTED))
    case _: UnsupportedOperationException ⇒
      Future.successful(GrpcMarshalling.status(Status.UNIMPLEMENTED))
    case other ⇒
      Future.failed(other)
  }
}
