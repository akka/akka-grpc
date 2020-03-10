/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.{ GrpcServiceException, Identity, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcProtocolNative, GrpcResponseHelpers, MissingParameterException }
import akka.http.scaladsl.model.HttpResponse
import io.grpc.Status

import scala.concurrent.{ ExecutionException, Future }

object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)
  private val UNIMPLEMENTED = Trailers(Status.UNIMPLEMENTED)

  @deprecated("To be removed", "grpc-web")
  def default(mapper: PartialFunction[Throwable, Trailers])(
      implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] = {
    implicit val writer: GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
    defaultHandler(mapper)
  }

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

  @deprecated("To be removed", "grpc-web")
  def default(implicit system: ActorSystem): PartialFunction[Throwable, Future[HttpResponse]] = {
    implicit val writer: GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
    defaultHandler(defaultMapper(system))
  }

  def defaultHandler(
      implicit system: ActorSystem,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    defaultHandler(defaultMapper(system))

  def defaultHandler(mapper: PartialFunction[Throwable, Trailers])(
      implicit system: ActorSystem,
      writer: GrpcProtocolWriter): PartialFunction[Throwable, Future[HttpResponse]] =
    mapper.orElse(defaultMapper(system)).andThen(s => Future.successful(GrpcResponseHelpers.status(s)))

}
