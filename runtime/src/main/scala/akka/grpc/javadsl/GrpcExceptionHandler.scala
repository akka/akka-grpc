/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import scala.concurrent.ExecutionException

import akka.actor.ActorSystem
import akka.grpc.{ GrpcErrorResponse, GrpcServiceException }
import akka.grpc.internal.MissingParameterException
import akka.http.javadsl.model.HttpResponse
import akka.japi.{ Function => jFunction }
import io.grpc.Status

object GrpcExceptionHandler {
  private val INTERNAL = GrpcErrorResponse(Status.INTERNAL)
  private val INVALID_ARGUMENT = GrpcErrorResponse(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = GrpcErrorResponse(Status.UNIMPLEMENTED)

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
        case grpcException: GrpcServiceException => GrpcErrorResponse(grpcException.getStatus, grpcException.metadata)
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
