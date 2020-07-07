/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import scala.concurrent.ExecutionContext
import java.util.concurrent.CompletionStage

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.http.scaladsl
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.server.RouteResult
import akka.japi.Function

import scala.compat.java8.FutureConverters

/**
 *  To be moved to akka-http
 */
@ApiMayChange
object RouteUtils {

  /** To be introduced as a directive in Akka HTTP 10.2.0 (but possibly for akka.japi.function.Function instead) */
  def fromFunction(
      handler: Function[HttpRequest, CompletionStage[HttpResponse]],
      system: ClassicActorSystemProvider): Route = {
    import scala.compat.java8.FutureConverters
    implicit val ec: ExecutionContext = system.classicSystem.dispatcher
    RouteAdapter { ctx =>
      FutureConverters
        .toScala(handler(ctx.request))
        .map(response => RouteResult.Complete(response.asInstanceOf[scaladsl.model.HttpResponse]))
    }
  }

  /** To be introduced as a static method on Route in Akka HTTP 10.2.0 */
  def toFunction(
      route: Route,
      system: ClassicActorSystemProvider): Function[HttpRequest, CompletionStage[HttpResponse]] = {
    implicit val sys = system
    implicit val ec: ExecutionContext = system.classicSystem.dispatcher
    val handler = scaladsl.server.Route.toFunction(route.asScala)

    (request: HttpRequest) => {
      import FutureConverters._
      handler(request.asInstanceOf[scaladsl.model.HttpRequest]).map(_.asInstanceOf[HttpResponse]).toJava
    }
  }
}
