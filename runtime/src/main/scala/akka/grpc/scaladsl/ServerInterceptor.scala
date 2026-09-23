/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.internal.ServerInterceptorSupport
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse

import scala.concurrent.Future

/**
 * Cross-cutting logic around gRPC service calls, such as authentication, logging or metrics.
 *
 * The interceptor runs before the request is unmarshalled. It can reject the call by returning a failed future,
 * for example with a [[akka.grpc.GrpcServiceException]], which is turned into a gRPC error response. It can pass
 * information to a PowerApi service implementation by adding request attributes, which are then available through
 * [[Metadata.attribute]]. It can also wrap the response future to observe the outcome of the call.
 *
 * Errors in streamed responses happen after the response future has completed and are not visible to interceptors.
 */
@ApiMayChange
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
      next: HttpRequest => Future[HttpResponse]): Future[HttpResponse]
}

@ApiMayChange
object ServerInterceptor {

  /**
   * Wraps a partial handler, such as the `partial` handler of a generated service or several services combined with
   * [[ServiceHandler.concat]], so that all calls it handles pass through the interceptors. The first interceptor is
   * the outermost.
   */
  def intercept(handler: PartialFunction[HttpRequest, Future[HttpResponse]], interceptors: ServerInterceptor*)(
      implicit system: ClassicActorSystemProvider): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    if (interceptors.isEmpty) handler
    else {
      val intercepted =
        ServerInterceptorSupport(handler, interceptors.map(i => i.intercept(_, _, _, _)), system)
      new PartialFunction[HttpRequest, Future[HttpResponse]] {
        override def isDefinedAt(request: HttpRequest): Boolean = handler.isDefinedAt(request)
        override def apply(request: HttpRequest): Future[HttpResponse] = intercepted(request)
      }
    }
  }
}
