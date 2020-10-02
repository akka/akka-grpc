/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.grpc.{ GrpcServiceException, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.javadsl.model.HttpResponse
import akka.japi.{ Function => JFunction }
import io.grpc.Status

import scala.concurrent.ExecutionException

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = Trailers(Status.UNIMPLEMENTED)

  // TODO this method is called from generated code, so can we 'just' change the type here?
  // This would cause problems, but this is likely fine because we announced the *HandlerFactory as ApiMayChange
  def defaultMapper: JFunction[ActorSystem, JFunction[Throwable, Trailers]] =
    new JFunction[ActorSystem, JFunction[Throwable, Trailers]] {
      override def apply(system: ActorSystem): JFunction[Throwable, Trailers] =
        default(system)
    }

  /** INTERNAL API */
  @InternalApi
  private def default(system: ActorSystem): JFunction[Throwable, Trailers] =
    new JFunction[Throwable, Trailers] {
      override def apply(param: Throwable): Trailers =
        param match {
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

  def standard(t: Throwable, writer: GrpcProtocolWriter, system: ClassicActorSystemProvider): HttpResponse =
    standard(t, default, writer, system)

  def standard(
      t: Throwable,
      mapper: JFunction[ActorSystem, JFunction[Throwable, Trailers]],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers.status(mapper(system.classicSystem)(t))(writer)
}
