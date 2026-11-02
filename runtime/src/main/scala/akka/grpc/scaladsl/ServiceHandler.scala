/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.GrpcProtocol
import akka.grpc.internal.{ GrpcProtocolWeb, GrpcProtocolWebText }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }

import scala.concurrent.Future

@ApiMayChange
object ServiceHandler {

  private[scaladsl] val notFound: Future[HttpResponse] = Future.successful(HttpResponse(StatusCodes.NotFound))

  private[scaladsl] val unsupportedMediaType: Future[HttpResponse] =
    Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))

  private def matchesVariant(variants: Set[GrpcProtocol])(request: jmodel.HttpRequest) =
    variants.exists(_.mediaTypes.contains(request.entity.getContentType.mediaType))

  private[grpc] val isGrpcWebRequest: jmodel.HttpRequest => Boolean = matchesVariant(
    Set(GrpcProtocolWeb, GrpcProtocolWebText))

  /**
   * Does not touch the request entity when no handler matches. An unread entity keeps its share of the HTTP/2
   * connection window.
   */
  @deprecated(
    "Use the overload taking the actor system, it discards the request entity when no handler matches",
    since = "2.5.14")
  def concatOrNotFound(
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    concat(handlers: _*).orElse { case _ => notFound }

  /**
   * Combines the handlers and responds with 404 when no handler matches. It then also discards the request entity.
   */
  def concatOrNotFound(
      system: ClassicActorSystemProvider,
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    concat(handlers: _*).orElse {
      case request =>
        request.discardEntityBytes(system)
        notFound
    }

  def concat(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*)
      : PartialFunction[HttpRequest, Future[HttpResponse]] =
    handlers.foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {
      case (acc, pf) => acc.orElse(pf)
    }
}
