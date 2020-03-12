/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.grpc.{ GrpcServiceException, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.javadsl.model.HttpResponse
import akka.japi.{ Function => jFunction }
import io.grpc.Status

import scala.concurrent.ExecutionException

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = Trailers(Status.UNIMPLEMENTED)

  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]] =
    new jFunction[ActorSystem, jFunction[Throwable, Trailers]] {
      override def apply(system: ActorSystem): jFunction[Throwable, Trailers] =
        default(system)
    }

  def default(system: ActorSystem): jFunction[Throwable, Trailers] =
    new jFunction[Throwable, Trailers] {
      override def apply(param: Throwable): Trailers = param match {
        case e: ExecutionException =>
          if (e.getCause == null) INTERNAL
          else default(system)(e.getCause)
        case e: CompletionException =>
          if (e.getCause == null) INTERNAL
          else default(system)(e.getCause)
        case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
        case _: MissingParameterException        => INVALID_ARGUMENT
        case _: NotImplementedError              => UNIMPLEMENTED
        case _: UnsupportedOperationException    => UNIMPLEMENTED
        case other =>
          system.log.error(other, "Unhandled error: [" + other.getMessage + "]")
          INTERNAL
      }
    }

  def standard(t: Throwable, writer: GrpcProtocolWriter, system: ActorSystem): HttpResponse =
    standard(t, default, writer, system)

  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]],
      writer: GrpcProtocolWriter,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers.status(mapper(system)(t))(writer)
}
