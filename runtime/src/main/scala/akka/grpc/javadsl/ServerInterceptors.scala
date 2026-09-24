/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerInterceptorSupport
import akka.grpc.scaladsl
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.{ model => smodel }
import akka.japi.function.{ Function => JFunction }

import java.util.concurrent.CompletionStage

import scala.annotation.varargs
import scala.concurrent.Future
import scala.jdk.FutureConverters._

@ApiMayChange
object ServerInterceptors {
  private type Handler = JFunction[HttpRequest, CompletionStage[HttpResponse]]

  /**
   * Wraps a handler so that all gRPC calls reaching it pass through the interceptors, for example a handler for
   * several services combined with [[ServiceHandler.concatOrNotFound]]. Calls to unknown services also pass through
   * the interceptors before the handler answers with 404. The first interceptor is the outermost. Calls with a path
   * deeper than `/service/method` are rejected with `INVALID_ARGUMENT` without reaching the handler.
   */
  @varargs
  def intercept(handler: Handler, system: ClassicActorSystemProvider, interceptors: ServerInterceptor*): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val errorMapper = GrpcExceptionHandler.defaultMapper(system.classicSystem)
      asJava(ServerInterceptorSupport.intercept(asScala(handler), interceptors.map(asScala), errorMapper(_)))
    }
  }

  /**
   * Wraps the handler of a single service so that only calls to that service pass through the interceptors. Calls
   * to other services are passed to the handler untouched, so the result can still be combined with other handlers
   * in [[ServiceHandler.concatOrNotFound]].
   */
  @varargs
  def intercept(
      service: ServiceDescription,
      handler: Handler,
      system: ClassicActorSystemProvider,
      interceptors: ServerInterceptor*): Handler =
    intercept(service.name, handler, system, interceptors: _*)

  /**
   * Wraps the handler of a single service registered under a custom prefix so that only calls to that prefix pass
   * through the interceptors.
   */
  @varargs
  def intercept(
      prefix: String,
      handler: Handler,
      system: ClassicActorSystemProvider,
      interceptors: ServerInterceptor*): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val intercepted = intercept(handler, system, interceptors: _*)
      request =>
        if (ServerInterceptorSupport.serviceName(request.asInstanceOf[smodel.HttpRequest].uri.path).contains(prefix))
          intercepted(request)
        else handler(request)
    }
  }

  private def asScala(handler: Handler): ServerInterceptorSupport.Handler =
    request => handler(request).asScala.asInstanceOf[Future[smodel.HttpResponse]]

  private def asJava(handler: ServerInterceptorSupport.Handler): Handler =
    request => (handler(request.asInstanceOf[smodel.HttpRequest]): Future[HttpResponse]).asJava

  private def asScala(interceptor: ServerInterceptor): scaladsl.ServerInterceptor =
    (service, method, request, next) =>
      interceptor.intercept(service, method, request, asJava(next)).asScala.asInstanceOf[Future[smodel.HttpResponse]]
}
