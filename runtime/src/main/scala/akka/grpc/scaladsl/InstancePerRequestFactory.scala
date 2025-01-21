/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.grpc.scaladsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.annotation.DoNotInherit
import akka.grpc.Trailers
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse

import scala.concurrent.Future

/**
 * Not for user extension, used by generated code.
 *
 * Internal abstraction for Akka gRPC integration in Akka SDK. Implemented only by the generated ScalaHandlerFactory for
 * Java gRPC service bootstrap when the codegen option generateScalaHandlerFactory is set.
 *
 * @tparam S the gRPC service type
 */
@DoNotInherit
@ApiMayChange
trait InstancePerRequestFactory[S] {
  def partialInstancePerRequest(
      serviceFactory: HttpRequest => S,
      prefix: String,
      eHandler: PartialFunction[Throwable, Trailers],
      systemProvider: ClassicActorSystemProvider): PartialFunction[HttpRequest, Future[HttpResponse]]

}
