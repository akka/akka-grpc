/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc.GrpcProtocol
import akka.grpc.Trailers
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri

import java.util.concurrent.CompletionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[grpc] object ServerInterceptorSupport {
  type Handler = HttpRequest => Future[HttpResponse]
  type Interceptor = (String, String, HttpRequest, Handler) => Future[HttpResponse]
  private type Chain = (String, String, HttpRequest) => Future[HttpResponse]

  private val unsupportedMediaType = HttpResponse(StatusCodes.UnsupportedMediaType)

  def serviceAndMethod(path: Uri.Path): Option[(String, String)] =
    path match {
      case Uri.Path.Slash(Uri.Path.Segment(service, Uri.Path.Slash(Uri.Path.Segment(method, Uri.Path.Empty)))) =>
        Some((service, method))
      case _ => None
    }

  def serviceName(path: Uri.Path): Option[String] =
    path match {
      case Uri.Path.Slash(Uri.Path.Segment(service, Uri.Path.Slash(_))) => Some(service)
      case _                                                            => None
    }

  /**
   * Maps a failure from an interceptor to a gRPC error response for the negotiated protocol. Set up once per
   * wrapped handler, the mapping itself is used per failed request.
   */
  final class ErrorMapper(system: ClassicActorSystemProvider) {
    private val defaultMapper = GrpcExceptionHandler.defaultMapper(system.classicSystem)
    private val mapper: PartialFunction[Throwable, Trailers] = {
      case e: CompletionException if e.getCause != null => defaultMapper(e.getCause)
      case e                                            => defaultMapper(e)
    }

    def errorResponse(request: HttpRequest, error: Throwable): HttpResponse =
      GrpcProtocol.negotiate(request) match {
        case Some((_, writer)) => GrpcResponseHelpers.status(mapper(error))(writer)
        case None              => unsupportedMediaType
      }
  }

  /**
   * Wraps the handler so that requests with a `/service/method` path go through the interceptors. Any failure is
   * turned into a gRPC error response. Requests with other paths are passed to the handler untouched.
   */
  def apply(handler: Handler, interceptors: Seq[Interceptor], system: ClassicActorSystemProvider): Handler = {
    if (interceptors.isEmpty) handler
    else {
      val errorMapper = new ErrorMapper(system)
      val chain = interceptors.foldRight[Chain]((_, _, request) => handler(request)) {
        (interceptor, next) => (service, method, request) =>
          interceptor(service, method, request, next(service, method, _))
      }

      request =>
        serviceAndMethod(request.uri.path) match {
          case None => handler(request)
          case Some((service, method)) =>
            val result =
              try chain(service, method, request)
              catch { case NonFatal(e) => Future.failed(e) }
            result.recover { case e => errorMapper.errorResponse(request, e) }(ExecutionContext.parasitic)
        }
    }
  }
}
