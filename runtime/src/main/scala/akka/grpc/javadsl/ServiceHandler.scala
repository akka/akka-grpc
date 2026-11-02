/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.http.javadsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.japi.function.{ Function => JFunction }

import scala.annotation.varargs

@ApiMayChange
object ServiceHandler {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[javadsl] val notFound: CompletionStage[HttpResponse] =
    CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND))

  /**
   * INTERNAL API
   */
  @InternalApi
  private[javadsl] val unsupportedMediaType: CompletionStage[HttpResponse] =
    CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.UNSUPPORTED_MEDIA_TYPE))

  /**
   * This is an alias for handler.
   */
  @varargs
  def concatOrNotFound(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    handler(handlers: _*)

  /**
   * This is an alias for handler.
   */
  @varargs
  def concatOrNotFound(
      system: ClassicActorSystemProvider,
      handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    handler(system, handlers: _*)

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - If the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   *
   * Does not touch the request entity when no handler matches. An unread entity keeps its share of the HTTP/2
   * connection window, so prefer `handler(system, handlers)`.
   */
  @varargs
  def handler(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val servicesHandler = concat(handlers: _*)
    (req: HttpRequest) => servicesHandler(req)
  }

  /**
   * Like `handler(handlers)`, but also discards the request entity when no handler matches.
   */
  @varargs
  def handler(system: ClassicActorSystemProvider, handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val discardNotFound: JFunction[HttpRequest, CompletionStage[HttpResponse]] = (req: HttpRequest) => {
      req.discardEntityBytes(system)
      notFound
    }
    handler(handlers :+ discardNotFound: _*)
  }

  private[javadsl] def concat(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    (req: HttpRequest) =>
      handlers.foldLeft(notFound) { (comp, next) =>
        comp.thenCompose(res => if (res.status == StatusCodes.NOT_FOUND) next.apply(req) else comp)
      }

}
