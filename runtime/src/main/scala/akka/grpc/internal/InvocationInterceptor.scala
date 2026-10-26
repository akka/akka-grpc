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
 * Wraps the invocation of each user service method in the generated SDK ScalaHandlerFactory. The interceptor is
 * called on the same thread that runs the user method. Picked up from the request attribute
 * [[InvocationInterceptor.AttributeKey]], when not present a no-op interceptor is used.
 */
@InternalStableApi
trait InvocationInterceptor {

  /**
   * @param request the incoming request, before the payload is decoded
   * @param method the gRPC method name
   * @param call invokes the user service method, must be called exactly once
   */
  def intercept[T](request: HttpRequest, method: String, call: Supplier[T]): T
}

/**
 * INTERNAL API
 */
@InternalStableApi
object InvocationInterceptor {

  val AttributeKey: AttributeKey[InvocationInterceptor] =
    akka.http.scaladsl.model.AttributeKey[InvocationInterceptor]("akka-grpc-invocation-interceptor")

  val NoOp: InvocationInterceptor = new InvocationInterceptor {
    override def intercept[T](request: HttpRequest, method: String, call: Supplier[T]): T = call.get()
  }
}
