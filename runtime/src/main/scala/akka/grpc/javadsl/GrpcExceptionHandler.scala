/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.grpc.{ GrpcServiceException, Trailers }
import akka.grpc.GrpcProtocol.GrpcProtocolWriter
import akka.grpc.internal.{ GrpcMetadataImpl, GrpcResponseHelpers, MissingParameterException }
import akka.http.javadsl.model.HttpResponse
import akka.japi.{ Function => jFunction }
import io.grpc.{ Status, StatusRuntimeException }

import scala.concurrent.ExecutionException
import akka.event.Logging

@ApiMayChange
object GrpcExceptionHandler {
  private val INTERNAL = Trailers(Status.INTERNAL)
  private val INVALID_ARGUMENT = Trailers(Status.INVALID_ARGUMENT)

  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]] =
    new jFunction[ActorSystem, jFunction[Throwable, Trailers]] {
      override def apply(system: ActorSystem): jFunction[Throwable, Trailers] = 
        default(system)
    }

  /** INTERNAL API */
  @InternalApi
  private def default(system: ActorSystem): jFunction[Throwable, Trailers] =
    new jFunction[Throwable, Trailers] {
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
          case e: NotImplementedError              => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
          case e: UnsupportedOperationException    => Trailers(Status.UNIMPLEMENTED.withDescription(e.getMessage))
          case e: StatusRuntimeException           => Trailers(e.getStatus, new GrpcMetadataImpl(e.getTrailers))
          case other =>
            val log = Logging(system, "akka.grpc.javadsl.GrpcExceptionHandler")
            log.error("********")
            log.error(other.getClass.toString)
            log.error("********")
            log.error(other, "Unhandled error: [{}]", other.getMessage)
            INTERNAL
        }
    }

  def standard(t: Throwable, writer: GrpcProtocolWriter, system: ClassicActorSystemProvider): HttpResponse =
    standard(t, default, writer, system)

  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, Trailers]],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider): HttpResponse =
    GrpcResponseHelpers.status(mapper(system.classicSystem)(t))(writer)
}
