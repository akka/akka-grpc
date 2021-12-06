/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.{ ApiMayChange, InternalStableApi }
import akka.grpc.{ GrpcServiceException, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcMetadataImpl, GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import io.grpc.{ Status, StatusRuntimeException }

import scala.concurrent.{ ExecutionException, Future }
import akka.event.Logging

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
    case e: StatusRuntimeException =>
      val meta = Option(e.getTrailers).getOrElse(new io.grpc.Metadata())
      Trailers(e.getStatus, new GrpcMetadataImpl(meta))
    case other =>
      val log = Logging(system, "akka.grpc.scaladsl.GrpcExceptionHandler")
      log.error(other, "Unhandled error: [{}]", other.getMessage)
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
