/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util
import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.actor.ActorSystem
import akka.grpc.scaladsl.{ ServiceHandler => sServiceHandler }
import akka.http.javadsl.marshalling.Marshaller
import akka.http.javadsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.marshalling.{ ToResponseMarshaller, Marshaller => sMarshaller }
import akka.http.scaladsl.server.directives.MarshallingDirectives
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import akka.stream.javadsl.{ Keep, Sink, Source }
import akka.NotUsed
// using japi because bindAndHandleAsync expects that
import akka.japi.{ Function => JFunction }
import akka.util.ConstantFun
import ch.megard.akka.http.cors.javadsl.settings.CorsSettings
import ch.megard.akka.http.cors.javadsl.CorsDirectives

import scala.annotation.varargs

object ServiceHandler {

  /** Default CORS settings to use for grpc-web */
  val defaultCorsSettings: CorsSettings = sServiceHandler.defaultCorsSettings

  private val notFound: CompletionStage[HttpResponse] =
    CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND))

  private val unsupportedMediaType: CompletionStage[HttpResponse] =
    CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.UNSUPPORTED_MEDIA_TYPE))

  private implicit val idUnmarshaller: Unmarshaller[HttpRequest, HttpRequest] =
    Unmarshaller.identityUnmarshaller[HttpRequest]

  // Adapt Marshaller.futureMarshaller(fromResponse) to javadsl
  private implicit val csResponseMarshaller: ToResponseMarshaller[CompletionStage[HttpResponse]] = {
    import scala.compat.java8.FutureConverters._
    // HACK: Only known way to lift this to the scaladsl.model types required for MarshallingDirectives.handleWith
    Marshaller.asScalaToResponseMarshaller(
      Marshaller
        .fromScala(sMarshaller.futureMarshaller(sMarshaller.opaque(ConstantFun.scalaIdentityFunction[HttpResponse])))
        .compose[CompletionStage[HttpResponse]](_.toScala))
  }

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
    val servicesHandler = concat(handlers: _*)
    (req: HttpRequest) => if (sServiceHandler.isGrpcRequest(req)) servicesHandler(req) else unsupportedMediaType
  }

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc-web` and `application/grpc-web-text` media types.
   *  - CORS is implemented for handled servives, including pre-flight requests and request enforcement.
   *  - If the request s not a CORS pre-flight request, and has an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def grpcWebHandler(
      handlers: util.List[JFunction[HttpRequest, CompletionStage[HttpResponse]]],
      as: ActorSystem,
      mat: Materializer): JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    grpcWebHandler(handlers, as, mat, defaultCorsSettings)

  /**
   * Creates a `HttpRequest` to `HttpResponse` handler for gRPC services that can be used in
   * for example `Http().bindAndHandleAsync` for the generated partial function handlers:
   *  - The generated handler supports the `application/grpc-web` and `application/grpc-web-text` media types.
   *  - CORS is implemented for handled servives, including pre-flight requests and request enforcement.
   *  - If the request s not a CORS pre-flight request, and has an invalid media type, then a _415: Unsupported Media Type_ response is produced.
   *  - Otherise if the request is not handled by one of the provided handlers, a _404: Not Found_ response is produced.
   */
  def grpcWebHandler(
      handlers: util.List[JFunction[HttpRequest, CompletionStage[HttpResponse]]],
      as: ActorSystem,
      mat: Materializer,
      corsSettings: CorsSettings): JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.collection.JavaConverters._
    val servicesHandler = concat(handlers.asScala: _*)
    val servicesRoute = RouteAdapter(MarshallingDirectives.handleWith(servicesHandler.apply(_)))
    val handler = asyncHandler(CorsDirectives.cors(corsSettings, () => servicesRoute), as, mat)
    (req: HttpRequest) =>
      if (sServiceHandler.isGrpcWebRequest(req) || sServiceHandler.isCorsPreflightRequest(req)) handler(req)
      else unsupportedMediaType
  }

  // Java version of Route.asyncHandler
  private def asyncHandler(
      route: Route,
      as: ActorSystem,
      mat: Materializer): HttpRequest => CompletionStage[HttpResponse] = {
    val sealedFlow =
      route.seal().flow(as, mat).toMat(Sink.head[HttpResponse], Keep.right[NotUsed, CompletionStage[HttpResponse]])
    (req: HttpRequest) => Source.single(req).runWith(sealedFlow, mat)
  }

  private def concat(handlers: JFunction[HttpRequest, CompletionStage[HttpResponse]]*)
      : JFunction[HttpRequest, CompletionStage[HttpResponse]] =
    (req: HttpRequest) =>
      handlers.foldLeft(notFound) { (comp, next) =>
        comp.thenCompose(res => if (res.status == StatusCodes.NOT_FOUND) next.apply(req) else comp)
      }

}
