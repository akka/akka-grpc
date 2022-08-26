/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

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
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - If the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  @varargs
  def handler(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val servicesHandler = concat(handlers: _*)
    (req: HttpRequest) => servicesHandler(req)
  }

  private[javadsl] def concat(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    (req: HttpRequest) =>
      handlers.foldLeft(notFound) { (comp, next) =>
        comp.thenCompose(res => if (res.status == StatusCodes.NOT_FOUND) next.apply(req) else comp)
      }

}
