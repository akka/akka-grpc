/**
  * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
  */
package akka.grpc.internal

import akka.annotation.InternalApi
import akka.stream.Materializer
import play.api.mvc.{ ControllerComponents, EssentialAction, RequestHeader }
import play.api.routing.Router
import play.api.routing.Router.Routes

/**
  * Boiler plate needed for the generated Play routers allowing for adding a service implementation in a Play app,
  * inherited by the generated abstract service router (both Java and Scala) which is then implemented by the user.
  *
  * INTERNAL API
  */
@InternalApi abstract class PlayRouterUsingActions(mat: Materializer, serviceName: String, cc: ControllerComponents)
  extends play.api.routing.Router {

  private val prefix = s"/$serviceName"

  /**
    * INTERNAL API
    */
  @InternalApi
  protected def createHandler(
                               serviceName: String,
                               mat: Materializer,
                               cc: ControllerComponents): RequestHeader => EssentialAction

  private val handler = createHandler(serviceName, mat, cc)

  // Scala API
  final override def routes: Routes = {
    case rh if rh.path.startsWith(prefix) ⇒ handler(rh)
  }

  final override def documentation: Seq[(String, String, String)] = Seq.empty

  /**
    * Registering a gRPC service under a custom prefix is not widely supported and strongly discouraged by the specification
    * so therefore not supported.
    */
  final override def withPrefix(prefix: String): Router =
    if (prefix == "/") this
    else
      throw new UnsupportedOperationException(
        "Prefixing gRPC services is not widely supported by clients, " +
          s"strongly discouraged by the specification and therefore not supported. " +
          s"Attempted to prefix with [$prefix], yet already default prefix known to be [${this.prefix}]. " +
          s"When binding gRPC routers the path in `routes` MUST BE `/`.")

}
