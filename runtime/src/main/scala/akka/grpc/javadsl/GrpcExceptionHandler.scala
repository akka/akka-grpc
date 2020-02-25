/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.lang.{ Iterable => jIterable }
import java.util.concurrent.CompletionException
import java.util.{ ArrayList => jArrayList }

import io.grpc.Status
import scala.concurrent.ExecutionException

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.internal.MissingParameterException
import akka.http.javadsl.model.{ HttpHeader, HttpResponse }
import akka.japi.{ Function => jFunction }

object GrpcExceptionHandler {
  private val NO_HEADERS: jIterable[HttpHeader] = new jArrayList[HttpHeader]();

  private val INVALID_ARGUMENT = GrpcErrorResponse(Status.INVALID_ARGUMENT, NO_HEADERS)
  private val INTERNAL = GrpcErrorResponse(Status.INTERNAL, NO_HEADERS)
  private val UNIMPLEMENTED = GrpcErrorResponse(Status.UNIMPLEMENTED, NO_HEADERS)

  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, GrpcErrorResponse]] =
    new jFunction[ActorSystem, jFunction[Throwable, GrpcErrorResponse]] {
      override def apply(system: ActorSystem): jFunction[Throwable, GrpcErrorResponse] =
        default(system)
    }

  def default(system: ActorSystem): jFunction[Throwable, GrpcErrorResponse] =
    new jFunction[Throwable, GrpcErrorResponse] {
      override def apply(param: Throwable): GrpcErrorResponse = param match {
        case e: ExecutionException =>
          if (e.getCause == null) INTERNAL
          else default(system)(e.getCause)
        case e: CompletionException =>
          if (e.getCause == null) INTERNAL
          else default(system)(e.getCause)
        case grpcException: GrpcServiceException => GrpcErrorResponse(grpcException.getStatus, grpcException.getHeaders)
        case _: MissingParameterException        => INVALID_ARGUMENT
        case _: NotImplementedError              => UNIMPLEMENTED
        case _: UnsupportedOperationException    => UNIMPLEMENTED
        case other =>
          system.log.error(other, "Unhandled error: [" + other.getMessage + "]")
          INTERNAL
      }
    }

  def standard(t: Throwable, system: ActorSystem): HttpResponse = standard(t, defaultMapper, system)

  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, GrpcErrorResponse]],
      system: ActorSystem): HttpResponse =
    GrpcMarshalling.status(mapper(system)(t))
}
