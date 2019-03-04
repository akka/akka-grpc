/**
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.annotation.InternalApi
import akka.dispatch.{ Dispatchers, ExecutionContexts }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import play.api.inject.Injector
import play.api.mvc.Handler
import play.api.mvc.akkahttp.AkkaHttpHandler
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.mvc.Http

import scala.concurrent.Future
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

/**
 * INTERNAL API
 */
@InternalApi private[akka] object PlayRouterHelper {
  def handlerFor(javaHandler: akka.japi.Function[akka.http.javadsl.model.HttpRequest, CompletionStage[akka.http.javadsl.model.HttpResponse]]): HttpRequest => Future[HttpResponse] = AkkaHttpHandler.apply(req =>
    javaHandler.apply(req.asInstanceOf[akka.http.javadsl.model.HttpRequest])
      .toScala
      .map(javaResp => javaResp.asInstanceOf[akka.http.scaladsl.model.HttpResponse])(ExecutionContexts.sameThreadExecutionContext))
}

/**
 * Boiler plate needed for the generated Play routers allowing for adding a service implementation in a Play app,
 * inherited by the generated abstract service router (both Java and Scala) which is then implemented by the user.
 *
 * INTERNAL API
 */
@InternalApi abstract class PlayRouter(val serviceName: String) extends play.api.routing.Router {

  private val prefix = s"/$serviceName"

  /**
   * INTERNAL API
   *
   * To be provided by (generated) concrete routers, only called internally
   */
  protected val respond: HttpRequest => Future[HttpResponse]

  private val handler = new AkkaHttpHandler {
    override def apply(request: HttpRequest): Future[HttpResponse] = respond(request)
  }

  // Scala API
  final override def routes: Routes = {
    case rh if rh.path.startsWith(prefix) ⇒ handler
  }

  final override def documentation: Seq[(String, String, String)] = Seq.empty

  /**
   * Registering a gRPC service under a custom prefix is not widely supported and strongly discouraged by the specification
   * so therefore not supported.
   */
  final override def withPrefix(prefix: String): Router =
    if (prefix == "/") this
    else
      throw new UnsupportedOperationException("Prefixing gRPC services is not widely supported by clients, " +
        s"strongly discouraged by the specification and therefore not supported. " +
        s"Attempted to prefix with [$prefix], yet already default prefix known to be [${this.prefix}]. " +
        s"When binding gRPC routers the path in `routes` MUST BE `/`.")

}
