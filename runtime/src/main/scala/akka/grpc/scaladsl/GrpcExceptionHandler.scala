/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcResponseHelpers
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ExecutionException, Future}

object GrpcExceptionHandler {

  def default(implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] = {
    case e: ExecutionException ⇒
      if (e.getCause == null) Future.failed(e)
      else handling(system)(e.getCause)
    case other ⇒
      handling(system)(other)
  }
  private def handling(implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] = {
    case grpcException: GrpcServiceException ⇒
      Future.successful(GrpcResponseHelpers.status(grpcException.status))
    case _: NotImplementedError ⇒
      Future.successful(GrpcResponseHelpers.status(Status.UNIMPLEMENTED))
    case _: UnsupportedOperationException ⇒
      Future.successful(GrpcResponseHelpers.status(Status.UNIMPLEMENTED))
    case other ⇒
      system.log.error(other, other.getMessage)
      Future.successful(GrpcResponseHelpers.status(Status.INTERNAL))
  }
}

