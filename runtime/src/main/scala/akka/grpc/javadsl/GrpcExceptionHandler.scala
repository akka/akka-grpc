/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException

import io.grpc.Status

import scala.concurrent.ExecutionException

import akka.actor.ActorSystem
import akka.grpc.{ Grpc, GrpcServiceException, Identity }
import akka.grpc.GrpcProtocol.GrpcProtocolMarshaller
import akka.grpc.internal.{ GrpcResponseHelpers, MissingParameterException }
import akka.http.javadsl.model.HttpResponse
import akka.japi.{ Function => jFunction }

object GrpcExceptionHandler {
  def defaultMapper: jFunction[ActorSystem, jFunction[Throwable, Status]] =
    new jFunction[ActorSystem, jFunction[Throwable, Status]] {
      override def apply(system: ActorSystem): jFunction[Throwable, Status] =
        default(system)
    }

  def default(system: ActorSystem): jFunction[Throwable, Status] = new jFunction[Throwable, Status] {
    override def apply(param: Throwable): Status = param match {
      case e: ExecutionException =>
        if (e.getCause == null) Status.INTERNAL
        else default(system)(e.getCause)
      case e: CompletionException =>
        if (e.getCause == null) Status.INTERNAL
        else default(system)(e.getCause)
      case grpcException: GrpcServiceException => grpcException.status
      case _: MissingParameterException        => Status.INVALID_ARGUMENT
      case _: NotImplementedError              => Status.UNIMPLEMENTED
      case _: UnsupportedOperationException    => Status.UNIMPLEMENTED
      case other =>
        system.log.error(other, "Unhandled error: [" + other.getMessage + "]")
        Status.INTERNAL
    }
  }

  @Deprecated
  def standard(t: Throwable, system: ActorSystem): HttpResponse = standard(t, defaultMapper, system)

  @Deprecated
  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, Status]],
      system: ActorSystem): HttpResponse =
    standard(t, mapper, Grpc.newMarshaller(Identity), system)

  def standard(t: Throwable, marshaller: GrpcProtocolMarshaller, system: ActorSystem): HttpResponse =
    standard(t, default, marshaller, system)

  def standard(
      t: Throwable,
      mapper: jFunction[ActorSystem, jFunction[Throwable, Status]],
      marshaller: GrpcProtocolMarshaller,
      system: ActorSystem): HttpResponse =
    GrpcResponseHelpers.status(mapper(system)(t))(marshaller)
}
