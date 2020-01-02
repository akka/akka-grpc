/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes

object ServiceHandler {
  private val notFound = Future.successful(HttpResponse(StatusCodes.NotFound))

  /**
   * Creates a [[HttpRequest]] to [[HttpResponse]] handler that can be used in for example `Http().bindAndHandleAsync`
   * for the generated partial function handlers and ends with `StatusCodes.NotFound` if the request is not matching.
   */
  def concatOrNotFound(
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    handlers
      .foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {
        case (acc, pf) => acc.orElse(pf)
      }
      .orElse { case _ => notFound }
}
