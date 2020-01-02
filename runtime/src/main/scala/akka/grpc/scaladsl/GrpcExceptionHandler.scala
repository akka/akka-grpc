/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ ExecutionException, Future }

object GrpcExceptionHandler {

  def default(mapper: PartialFunction[Throwable, Status])(
      implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, Status] = {
    case e: ExecutionException =>
      if (e.getCause == null) Status.INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => grpcException.status
    case _: NotImplementedError              => Status.UNIMPLEMENTED
    case _: UnsupportedOperationException    => Status.UNIMPLEMENTED
    case _: MissingParameterException        => Status.INVALID_ARGUMENT
    case other =>
      system.log.error(other, s"Unhandled error: [${other.getMessage}].")
      Status.INTERNAL
  }

  def default(implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] =
    default(defaultMapper(system))
}
