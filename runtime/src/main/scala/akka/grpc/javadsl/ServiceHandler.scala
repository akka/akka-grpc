/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// using japi because bindAndHandleAsync expects that
import akka.japi.{ Function => JFunction }

import scala.annotation.varargs

import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.StatusCodes

object ServiceHandler {
  private val notFound = CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND))

  /**
   * Creates a [[HttpRequest]] to [[HttpResponse]] handler that can be used in for example
   * `Http.get(system).bindAndHandleAsync` for the generated handlers and ends with `StatusCodes.NotFound`
   * if the request is not matching.
   */
  @varargs def concatOrNotFound(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    def cont(
        req: HttpRequest,
        response: CompletionStage[HttpResponse],
        remainingHandlers: List[JFunction[HttpRequest, CompletionStage[HttpResponse]]]): CompletionStage[HttpResponse] =
      remainingHandlers match {
        case Nil => response
        case head :: tail =>
          response.thenCompose(javaFunction { rsp =>
            if (rsp.status == StatusCodes.NOT_FOUND) {
              val nextResponse = head(req)
              cont(req, nextResponse, tail)
            } else
              response
          })
      }

    new JFunction[HttpRequest, CompletionStage[HttpResponse]] {
      override def apply(req: HttpRequest): CompletionStage[HttpResponse] =
        cont(req, notFound, handlers.toList)
    }
  }
}
