/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcResponseHelpers
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionException, Future }

object GrpcExceptionHandler {

  private val log = LoggerFactory.getLogger(getClass)

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
      log.error(other.getMessage, other)
      Future.successful(GrpcResponseHelpers.status(Status.INTERNAL))
  }
}

