/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.{ ApiMayChange, InternalStableApi }
import akka.grpc.{ GrpcServiceException, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ ExecutionException, Future }

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)

  def defaultMapper(system: ActorSystem): PartialFunction[Throwable, Trailers] = {
    case e: ExecutionException =>
      if (e.getCause == null) INTERNAL
      else defaultMapper(system)(e.getCause)
    case grpcException: GrpcServiceException => Trailers(grpcException.status, grpcException.metadata)
    case e: NotImplementedError              => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
    case e: UnsupportedOperationException    => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
    case _: MissingParameterException        => INVALID_ARGUMENT
    case other =>
      system.log.error(other, s"Unhandled error: [${other.getMessage}].")
      INTERNAL
  }

  @InternalStableApi
  def default(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    from(defaultMapper(system.classicSystem))

  @InternalStableApi
  def from(mapper: PartialFunction[Throwable, Trailers])(
      implicit system: ClassicActorSystemProvider,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system.classicSystem)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

}
