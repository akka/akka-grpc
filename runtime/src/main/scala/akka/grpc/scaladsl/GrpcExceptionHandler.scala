/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import scala.concurrent.{ ExecutionException, Future }

import akka.grpc.{ GrpcErrorResponse, GrpcServiceException }
import io.grpc.Status

object GrpcExceptionHandler {
  private val INTERNAL = GrpcErrorResponse(Status.INTERNAL)
  private val INVALID_ARGUMENT = GrpcErrorResponse(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = GrpcErrorResponse(Status.UNIMPLEMENTED)

  def default(mapper: PartialFunction[Throwable, GrpcErrorResponse])(
      implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, GrpcErrorResponse] = {
    case e: ExecutionException =>
      if (e.getCause == null) INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => GrpcErrorResponse(grpcException.status, grpcException.metadata)
    case _: NotImplementedError              => UNIMPLEMENTED
    case _: UnsupportedOperationException    => UNIMPLEMENTED
    case _: MissingParameterException        => INVALID_ARGUMENT
    case other =>
      system.log.error(other, s"Unhandled error: [${other.getMessage}].")
      INTERNAL
  }

  def default(implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] =
    default(defaultMapper(system))
}
