/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.{ ExecutionException, Future }
import io.grpc.Status
import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcResponseHelpers
import akka.http.scaladsl.model.HttpResponse

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
      Future.successful(GrpcResponseHelpers.status(grpcException.status))
    case _: NotImplementedError ⇒
      Future.successful(GrpcResponseHelpers.status(Status.UNIMPLEMENTED))
    case _: UnsupportedOperationException ⇒
      Future.successful(GrpcResponseHelpers.status(Status.UNIMPLEMENTED))
    case other ⇒
      Future.failed(other)
  }
}

