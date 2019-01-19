/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletionException
import akka.grpc.GrpcServiceException
import akka.http.javadsl.model.HttpResponse
import io.grpc.Status
import akka.japi.Function

import scala.concurrent.ExecutionException

object GrpcExceptionHandler {
  def defaultMapper: Function[Throwable, Status] = {
    case e: ExecutionException ⇒
      if (e.getCause == null) Status.INTERNAL
      else defaultMapper(e.getCause)
    case e: CompletionException ⇒
      if (e.getCause == null) Status.INTERNAL
      else defaultMapper(e.getCause)
    case grpcException: GrpcServiceException ⇒ grpcException.status
    case _: NotImplementedError ⇒ Status.UNIMPLEMENTED
    case _: UnsupportedOperationException ⇒ Status.UNIMPLEMENTED
    case other ⇒
      println(other)
      Status.INTERNAL
  }

  def standard(t: Throwable): HttpResponse = standard(t, defaultMapper)

  def standard(t: Throwable, mapper: Function[Throwable, Status]): HttpResponse = {
    println(s"Caught exception $t")
    GrpcMarshalling.status(mapper(t))
  }
}
