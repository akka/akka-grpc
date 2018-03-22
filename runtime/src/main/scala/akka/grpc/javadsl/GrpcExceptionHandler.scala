package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import akka.grpc.GrpcServiceException

import scala.concurrent.ExecutionException
import akka.http.javadsl.model.HttpResponse
import io.grpc.Status

object GrpcExceptionHandler {
  def standard(t: Throwable): HttpResponse = t match {
    case e: ExecutionException ⇒
      if (e.getCause == null) GrpcMarshalling.status(Status.INTERNAL)
      else handling(e.getCause)
    case e: CompletionException ⇒
      if (e.getCause == null) GrpcMarshalling.status(Status.INTERNAL)
      else handling(e.getCause)
    case other ⇒
      handling(other)
  }
  private def handling(t: Throwable): HttpResponse = t match {
    case grpcException: GrpcServiceException ⇒
      GrpcMarshalling.status(grpcException.status)
    case _: NotImplementedError ⇒
      GrpcMarshalling.status(Status.UNIMPLEMENTED)
    case _: UnsupportedOperationException ⇒
      GrpcMarshalling.status(Status.UNIMPLEMENTED)
    case other ⇒
      println(other)
      GrpcMarshalling.status(Status.INTERNAL)
  }
}
