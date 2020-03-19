/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.annotation.{ ApiMayChange, InternalStableApi }
import akka.grpc.{ GrpcServiceException, ProtobufSerialization, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ ExecutionException, Future }

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = Trailers(Status.UNIMPLEMENTED)

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, Trailers] = {
    case e: ExecutionException =>
      if (e.getCause == null) INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
    case _: NotImplementedError              => UNIMPLEMENTED
    case _: UnsupportedOperationException    => UNIMPLEMENTED
    case _: MissingParameterException        => INVALID_ARGUMENT
    case other =>
      system.log.error(other, s"Unhandled error: [${other.getMessage}].")
      INTERNAL
  }

  @InternalStableApi
  def default(
      implicit system: ActorSystem,
      writer: GrpcProtocolWriter,
      format: ProtobufSerialization): PartialFunction[Throwable, Future[HttpResponse]] =
    from(defaultMapper(system))

  @InternalStableApi
  def from(mapper: PartialFunction[Throwable, Trailers])(
      implicit system: ActorSystem,
      writer: GrpcProtocolWriter,
      format: ProtobufSerialization): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

}
