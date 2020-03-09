/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcProtocol
import akka.grpc.internal.{ GrpcProtocolNative, GrpcProtocolWeb, GrpcProtocolWebText }
import akka.http.javadsl.{ model => jmodel }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MarshallingDirectives.handleWith
import akka.stream.Materializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import scala.collection.immutable
import scala.concurrent.Future

object ServiceHandler {

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

  private val handlerNotFound: PartialFunction[HttpRequest, Future[HttpResponse]] = {
    case _ => Future.successful(HttpResponse(StatusCodes.NotFound))
  }

  private val unsupportedMediaType: Future[HttpResponse] =
    Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))

  private def matchesVariant(variants: Set[GrpcProtocol])(request: jmodel.HttpRequest) =
    variants.exists(_.mediaTypes.contains(request.entity.getContentType.mediaType))

  private[grpc] val isGrpcRequest: jmodel.HttpRequest => Boolean = matchesVariant(Set(GrpcProtocolNative))
  private[grpc] val isGrpcWebRequest: jmodel.HttpRequest => Boolean = matchesVariant(
    Set(GrpcProtocolWeb, GrpcProtocolWebText))

  private[grpc] def isCorsPreflightRequest(r: jmodel.HttpRequest): Boolean =
    r.method == HttpMethods.OPTIONS && r.getHeader(classOf[Origin]).isPresent && r
      .getHeader(classOf[`Access-Control-Request-Method`])
      .isPresent

  /**
   * This is an alias for handler
   */
  def concatOrNotFound(
      handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] =
    handler(handlers: _*)

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc` media type.
   *  - If the request is for a handled service, but with an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def handler(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*): HttpRequest => Future[HttpResponse] = {
    val servicesHandler: PartialFunction[HttpRequest, Future[HttpResponse]] = concat(handlers: _*)
    ({
      case r if servicesHandler.isDefinedAt(r) => if (isGrpcRequest(r)) servicesHandler(r) else unsupportedMediaType
    }: PartialFunction[HttpRequest, Future[HttpResponse]]).orElse(handlerNotFound)
  }

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
    val servicesHandler = concat(handlers: _*)
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

  private def concat(handlers: PartialFunction[HttpRequest, Future[HttpResponse]]*)
      : PartialFunction[HttpRequest, Future[HttpResponse]] =
    handlers.foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {
      case (acc, pf) => acc.orElse(pf)
    }

}
