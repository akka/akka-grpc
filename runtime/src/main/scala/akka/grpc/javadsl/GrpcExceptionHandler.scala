/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.http.javadsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.ExecutionException

object GrpcExceptionHandler {

  def standard(t: Throwable, system: ActorSystem): HttpResponse = t match {
    case e: ExecutionException ⇒
      if (e.getCause == null) GrpcMarshalling.status(Status.INTERNAL)
      else handling(e.getCause, system)
    case e: CompletionException ⇒
      if (e.getCause == null) GrpcMarshalling.status(Status.INTERNAL)
      else handling(e.getCause, system)
    case other ⇒
      handling(other, system)
  }
  private def handling(t: Throwable, system: ActorSystem): HttpResponse = t match {
    case grpcException: GrpcServiceException ⇒
      GrpcMarshalling.status(grpcException.status)
    case _: NotImplementedError ⇒
      GrpcMarshalling.status(Status.UNIMPLEMENTED)
    case _: UnsupportedOperationException ⇒
      GrpcMarshalling.status(Status.UNIMPLEMENTED)
    case other ⇒
      system.log.error(other, other.getMessage)
      GrpcMarshalling.status(Status.INTERNAL)
  }
}
