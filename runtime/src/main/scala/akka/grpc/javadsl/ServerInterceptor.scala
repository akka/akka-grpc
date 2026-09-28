/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.ApiMayChange
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.japi.function.{ Function => JFunction }

import java.util.concurrent.CompletionStage

/**
 * Cross-cutting logic around gRPC service calls, such as authentication, logging or metrics.
 *
 * The interceptor runs before the request is unmarshalled. It can reject the call by returning a failed
 * `CompletionStage`, for example with a [[akka.grpc.GrpcServiceException]], which is turned into a gRPC error
 * response using the default exception mapping of [[GrpcExceptionHandler]]. It can pass information to a Power API
 * service implementation by adding request attributes, which are then available through [[Metadata.getAttribute]].
 * It can also wrap the response to observe the outcome of the call.
 *
 * The interceptor is called from the server stream and must not block. The request entity is a stream that can
 * only be consumed once, so an interceptor that reads it must not pass the same request to `next`.
 *
 * Errors in streamed responses happen after the response headers are sent and are not visible to interceptors.
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
