/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerInterceptorSupport
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.{ model => smodel }
import akka.japi.function.{ Function => JFunction }

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.annotation.varargs
import scala.util.control.NonFatal

/**
 * Cross-cutting logic around gRPC service calls, such as authentication, logging or metrics.
 *
 * The interceptor runs before the request is unmarshalled. It can reject the call by returning a failed
 * `CompletionStage`, for example with a [[akka.grpc.GrpcServiceException]], which is turned into a gRPC error
 * response. It can pass information to a PowerApi service implementation by adding request attributes, which are
 * then available through [[Metadata.getAttribute]]. It can also wrap the response to observe the outcome of the call.
 *
 * Errors in streamed responses happen after the response has completed and are not visible to interceptors.
 */
@ApiMayChange
@FunctionalInterface
trait ServerInterceptor {

  /**
   * @param serviceName the gRPC service name from the request path, without the leading slash
   * @param methodName the gRPC method name from the request path
   * @param next the rest of the chain, ending with the service handler
   */
  def intercept(
      serviceName: String,
      methodName: String,
      request: HttpRequest,
      next: java.util.function.Function[HttpRequest, CompletionStage[HttpResponse]]): CompletionStage[HttpResponse]
}

@ApiMayChange
object ServerInterceptors {
  private type Handler = JFunction[HttpRequest, CompletionStage[HttpResponse]]
  private type Chain = (String, String, HttpRequest) => CompletionStage[HttpResponse]

  /**
   * Wraps a handler so that all gRPC calls reaching it pass through the interceptors, for example a handler for
   * several services combined with [[ServiceHandler.concatOrNotFound]]. The first interceptor is the outermost.
   */
  @varargs
  def intercept(handler: Handler, system: ClassicActorSystemProvider, interceptors: ServerInterceptor*): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val errorMapper = new ServerInterceptorSupport.ErrorMapper(system)
      val chain = interceptors.foldRight[Chain]((_, _, request) => handler(request)) {
        (interceptor, next) => (service, method, request) =>
          interceptor.intercept(service, method, request, next(service, method, _))
      }

      request =>
        ServerInterceptorSupport.serviceAndMethod(request.asInstanceOf[smodel.HttpRequest].uri.path) match {
          case None => handler(request)
          case Some((service, method)) =>
            val result =
              try chain(service, method, request)
              catch { case NonFatal(e) => CompletableFuture.failedFuture[HttpResponse](e) }
            result.exceptionally(e => errorMapper.errorResponse(request.asInstanceOf[smodel.HttpRequest], e))
        }
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
      interceptors: ServerInterceptor*): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val intercepted = intercept(handler, system, interceptors: _*)
      request =>
        if (ServerInterceptorSupport
            .serviceName(request.asInstanceOf[smodel.HttpRequest].uri.path)
            .contains(service.name))
          intercepted(request)
        else handler(request)
    }
  }
}
