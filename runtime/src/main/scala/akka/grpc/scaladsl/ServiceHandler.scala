/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.{ GrpcProtocol, GrpcProtocolNative, GrpcProtocolWeb, GrpcWebTextProtocol }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.model.headers.{ `Access-Control-Request-Method`, Origin }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MarshallingDirectives.handleWith
import akka.stream.Materializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import scala.collection.immutable
import scala.concurrent.Future

object ServiceHandler {

  private val handlerNotFound: PartialFunction[HttpRequest, Future[HttpResponse]] = {
    case _ => Future.successful(HttpResponse(StatusCodes.NotFound))
  }

  private def matchesVariant(variants: Set[GrpcProtocol])(request: HttpRequest) =
    variants.exists(_.mediaTypes.contains(request.entity.contentType.mediaType))

  private val isGrpcRequest: HttpRequest => Boolean = matchesVariant(Set(GrpcProtocolNative))
  private val isGrpcWebRequest: HttpRequest => Boolean = matchesVariant(Set(GrpcProtocolWeb, GrpcWebTextProtocol))

  private def isCorsPreflightRequest(r: HttpRequest): Boolean =
    r.method == HttpMethods.OPTIONS && r.header[Origin].isDefined && r.header[`Access-Control-Request-Method`].isDefined

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function
   * handlers and ends with `StatusCodes.NotFound` if the request is not
   * matching.
   */
  @deprecated("Use ServiceHandler.newBuilder instead", "grpc-web")
  def concatOrNotFound(
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    concat(handlers: _*).orElse(handlerNotFound)

  private[scaladsl] def concat(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*)
      : PartialFunction[HttpRequest, Future[HttpResponse]] =
    handlers.foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {
      case (acc, pf) => acc.orElse(pf)
    }

  /**
   * Construct a builder to produce a service handler for gRPC responses.
   */
  def newBuilder = new ServiceHandlerBuilder

  /**
   * A builder to produce handlers for use in `Http().bindAndHandleAsync` to serve gRPC requests.
   */
  final class ServiceHandlerBuilder {

    private var grpcServices = immutable.Seq.empty[PartialFunction[HttpRequest, Future[HttpResponse]]]
    private var grpcWebServices = immutable.Seq.empty[PartialFunction[HttpRequest, Future[HttpResponse]]]
    private var corsSettings: CorsSettings = GrpcProtocolWeb.defaultCorsSettings

    /**
     * Specifies a set of gRPC services to be served using the `application-grpc+proto` protocol.
     *
     * @param partials the partial handler functions of the gRPC services.
     */
    def withGrpc(partials: PartialFunction[HttpRequest, Future[HttpResponse]]*): this.type = {
      grpcServices = grpcServices ++ partials
      this
    }

    /**
     * Specifies a set of gRPC services to be served using the `application-grpc-web+proto` and `application-grpc-web-text+proto` protocols.
     *
     * @param partials the partial handler functions of the gRPC services.
     */
    def withGrpcWeb(partials: PartialFunction[HttpRequest, Future[HttpResponse]]*): this.type = {
      grpcWebServices = grpcWebServices ++ partials
      this
    }

    /**
     * Specifies custom CORS settings for grpc-web serving.
     */
    def withCorsSettings(settings: CorsSettings): this.type = {
      corsSettings = settings
      this
    }

    /**
     * Produces a partial function that will service the provided gRPC and gRPC-web services:
     *   - `application/grpc` requests will be served by the specified [[withGrpc gRPC services]].
     *   - `application/grpc` and `application-grpc-web` requests will be served by the specified [[withGrpcWeb gRPC-web services]].
     *   - CORS pre-flight requests for one of the [[withGrpcWeb gRPC-web services]] will be responded to.
     *   - The constructed partial function will not be defined for any other request content type, or requests for an unknown/unhandled service.
     */
    def asPartial(implicit as: ActorSystem, mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
      concat(Seq(if (grpcServices.nonEmpty) {
        val servicesHandler: PartialFunction[HttpRequest, Future[HttpResponse]] = concat(grpcServices: _*)
        Some({
          case r if isGrpcRequest(r) && servicesHandler.isDefinedAt(r) => servicesHandler(r)
        }: PartialFunction[HttpRequest, Future[HttpResponse]])
      } else None, if (grpcWebServices.nonEmpty) {
        val servicesHandler = concat(grpcWebServices: _*)
        // HACK: Cors is only exposed as a Directive, and Route.asyncHandler produces a sealed route
        //       so make sure that handler is only entered if the underlying grpc-web services would serve the request
        val grpcWebHandler = Route.asyncHandler(cors(corsSettings) { handleWith(servicesHandler) })
        Some({
          case r if (isGrpcWebRequest(r) || isCorsPreflightRequest(r)) && servicesHandler.isDefinedAt(r) =>
            grpcWebHandler(r)
        }: PartialFunction[HttpRequest, Future[HttpResponse]])
      } else None).flatten: _*)
    }

    /**
     * Produces a complete handler function that can service the provided gRPC and gRPC-web services in `Http().bindAndHandleAsync`.
     *
     * This functions as per [[asPartial]], with the addition of:
     *  - Any request for a content type not being serviced (e.g. if no gRPC-web services are specified) will result in a `415: Unsupported Media Type` response.
     *  - Unhandled requests will result in a `404: Not Found` response.
     **/
    def asHandler(implicit as: ActorSystem, mat: Materializer): HttpRequest => Future[HttpResponse] = {
      asPartial.orElse {
        case r if isCorsPreflightRequest(r) => Future.successful(HttpResponse(StatusCodes.NotFound))
        case _                              => Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))
      }
    }
  }
}
