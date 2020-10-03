/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.grpc.scaladsl.{ ServiceHandler => sServiceHandler }
import akka.http.javadsl.model.{ HttpRequest, HttpResponse, StatusCodes }

// using japi because bind expects that.
import akka.japi.{ Function => JFunction }

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
   *  - The generated handler supports the `application/grpc` media type.
   *  - If the request is for an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherwise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  @varargs
  def handler(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val servicesHandler = _concat(handlers: _*)
    (req: HttpRequest) => if (sServiceHandler.isGrpcRequest(req)) servicesHandler(req) else unsupportedMediaType
  }

  @varargs
  def concat(handlers: akka.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]]*)
      : akka.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]] = {
    val servicesHandler = new akka.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]]() {
      override def apply(httpRequest: HttpRequest): CompletionStage[HttpResponse] = {
        handler(handlers.map(h =>
          new JFunction[HttpRequest, CompletionStage[HttpResponse]] {
            override def apply(param: HttpRequest): CompletionStage[HttpResponse] = {
              h.apply(param)
            }
          }): _*).apply(httpRequest)
      }
    }
    (req: HttpRequest) => if (sServiceHandler.isGrpcRequest(req)) servicesHandler(req) else unsupportedMediaType
  }

  private[javadsl] def _concat(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    (req: HttpRequest) =>
      handlers.foldLeft(notFound) { (comp, next) =>
        comp.thenCompose(res => if (res.status == StatusCodes.NOT_FOUND) next.apply(req) else comp)
      }

}
