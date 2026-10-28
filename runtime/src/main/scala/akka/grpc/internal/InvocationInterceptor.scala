/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.annotation.InternalStableApi
import akka.http.javadsl.model.HttpRequest
import akka.http.scaladsl.model.AttributeKey

import java.util.function.Supplier

/**
 * INTERNAL API
 *
 * Wraps the invocation of each user service method in the generated SDK ScalaHandlerFactory, run synchronously inside
 * `intercept` on the same thread. Picked up from the request attribute [[InvocationInterceptor.Key]], when not present
 * [[InvocationInterceptor.NoOp]] is used. The service instance is created before the request is unmarshalled, outside
 * of the interceptor.
 *
 * Only that synchronous part is covered. User code that runs later, on other threads, runs outside of the interceptor:
 * continuations of a returned `CompletionStage`, the stages of a returned `Source` and the processing of the incoming
 * `Source` for client streaming methods.
 */
@InternalStableApi
trait InvocationInterceptor {

  /**
   * Implementations must invoke `call` exactly once and return its result. Any thread local state set up around
   * `call` must be restored in a `finally`, since the thread is shared with other requests.
   *
   * @param request the incoming request, its entity is already consumed, or being consumed, and must not be read
   * @param method the gRPC method name, without the service name, for example `SayHello`
   * @param call invokes the user service method
   */
  def intercept[T](request: HttpRequest, method: String, call: Supplier[T]): T
}

/**
 * INTERNAL API
 */
@InternalStableApi
object InvocationInterceptor {

  val Key: AttributeKey[InvocationInterceptor] = AttributeKey[InvocationInterceptor]("akka-grpc-invocation-interceptor")

  object NoOp extends InvocationInterceptor {
    override def intercept[T](request: HttpRequest, method: String, call: Supplier[T]): T = call.get()
  }
}
