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

import java.util.concurrent.CompletionStage

import scala.annotation.varargs
import scala.concurrent.Future
import scala.jdk.FutureConverters._

/**
 * Cross-cutting logic around gRPC service calls, such as authentication, logging or metrics.
 *
 * The interceptor runs before the request is unmarshalled. It can reject the call by returning a failed
 * `CompletionStage`, for example with a [[akka.grpc.GrpcServiceException]], which is turned into a gRPC error
 * response using the default exception mapping of [[GrpcExceptionHandler]]. It can pass information to a Power API
 * service implementation by adding request attributes, which are then available through [[Metadata.getAttribute]].
 * It can also wrap the response to observe the outcome of the call.
 *
 * The interceptor is called on the connection thread and must not block. The request entity is a stream that can
 * only be consumed once, so an interceptor that reads it must not pass the same request to `next`.
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
  @throws(classOf[Exception])
  def intercept(
      serviceName: String,
      methodName: String,
      request: HttpRequest,
      next: JFunction[HttpRequest, CompletionStage[HttpResponse]]): CompletionStage[HttpResponse]
}

@ApiMayChange
object ServerInterceptors {
  private type Handler = JFunction[HttpRequest, CompletionStage[HttpResponse]]

  /**
   * Wraps a handler so that all gRPC calls reaching it pass through the interceptors, for example a handler for
   * several services combined with [[ServiceHandler.concatOrNotFound]]. Calls to unknown services also pass through
   * the interceptors before the handler answers with 404. The first interceptor is the outermost.
   */
  @varargs
  def intercept(handler: Handler, system: ClassicActorSystemProvider, interceptors: ServerInterceptor*): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val intercepted = ServerInterceptorSupport(asScala(handler), interceptors.map(asScala), system)
      request => (intercepted(request.asInstanceOf[smodel.HttpRequest]): Future[HttpResponse]).asJava
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

  private def asScala(interceptor: ServerInterceptor): ServerInterceptorSupport.Interceptor =
    (service, method, request, next) =>
      interceptor
        .intercept(
          service,
          method,
          request,
          r => (next(r.asInstanceOf[smodel.HttpRequest]): Future[HttpResponse]).asJava)
        .asScala
        .asInstanceOf[Future[smodel.HttpResponse]]
}
