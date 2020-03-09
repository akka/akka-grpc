package akka.grpc.scaladsl

import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.grpc.scaladsl.ServiceHandler.{ handlerNotFound, isGrpcWebRequest, unsupportedMediaType }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse }
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MarshallingDirectives.handleWith
import akka.stream.Materializer

import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

object WebHandler {

  /** Default CORS settings to use for grpc-web */
  val defaultCorsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowCredentials(true)
    .withAllowedMethods(immutable.Seq(HttpMethods.POST, HttpMethods.OPTIONS))
    .withExposedHeaders(immutable.Seq(headers.`Status`.name, headers.`Status-Message`.name, `Content-Encoding`.name))
    .withAllowedHeaders(
      HttpHeaderRange(
        "x-user-agent",
        "x-grpc-web",
        `Content-Type`.name,
        Accept.name,
        "grpc-timeout",
        `Accept-Encoding`.name))

  private[grpc] def isCorsPreflightRequest(r: jmodel.HttpRequest): Boolean =
    r.method == HttpMethods.OPTIONS && r.getHeader(classOf[Origin]).isPresent && r
      .getHeader(classOf[`Access-Control-Request-Method`])
      .isPresent

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc-web` and `application/grpc-web-text` media types.
   *  - CORS is implemented for handled servives, including pre-flight requests and request enforcement.
   *  - If the request is for a handled service, is not a CORS pre-flight request, and has an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def grpcWebHandler(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*)(
      implicit as: ActorSystem,
      mat: Materializer,
      corsSettings: CorsSettings = defaultCorsSettings): HttpRequest => Future[HttpResponse] = {
    val servicesHandler = ServiceHandler.concat(handlers: _*)
    // HACK: Cors is only exposed as a Directive, and Route.asyncHandler produces a sealed route
    //       so make sure that handler is only entered if the underlying grpc-web services would serve the request
    val grpcWebHandler = Route.asyncHandler(cors(corsSettings) {
      handleWith(servicesHandler)
    })

    ({
      case r if servicesHandler.isDefinedAt(r) =>
        if (isGrpcWebRequest(r) || isCorsPreflightRequest(r)) grpcWebHandler(r) else unsupportedMediaType
    }: PartialFunction[HttpRequest, Future[HttpResponse]]).orElse(handlerNotFound)
  }

}
