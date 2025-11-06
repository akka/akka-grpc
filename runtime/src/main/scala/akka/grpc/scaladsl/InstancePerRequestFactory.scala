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
import akka.stream.Materializer
import akka.stream.SystemMaterializer

import scala.annotation.nowarn
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

  // Note: this may be strange, one of these two methods is always overridden, either the old one, or the new
  // having this cycle avoids having to implement the old method in new generated code just for compatibility

  @nowarn("msg=deprecated")
  def partialInstancePerRequest(
      serviceFactory: HttpRequest => S,
      prefix: String,
      eHandler: PartialFunction[Throwable, Trailers],
      systemProvider: ClassicActorSystemProvider,
      materializer: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    partialInstancePerRequest(serviceFactory, prefix, eHandler, systemProvider)
  }

  // for compatibility with existing generated sources and SDK
  @deprecated(since = "2.5.9")
  def partialInstancePerRequest(
      serviceFactory: HttpRequest => S,
      prefix: String,
      eHandler: PartialFunction[Throwable, Trailers],
      systemProvider: ClassicActorSystemProvider): PartialFunction[HttpRequest, Future[HttpResponse]] =
    partialInstancePerRequest(
      serviceFactory,
      prefix,
      eHandler,
      systemProvider,
      SystemMaterializer(systemProvider).materializer)

}
