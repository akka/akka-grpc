/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc.GrpcProtocol
import akka.grpc.GrpcServiceException
import akka.grpc.Trailers
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import io.grpc.Status

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
      // failures from dependent CompletionStages in Java interceptors arrive wrapped
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
   * turned into a gRPC error response. Other paths under a service are rejected with `INVALID_ARGUMENT`, like the
   * generated handlers do. Paths that do not look like a service call are passed to the handler untouched.
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
        request.uri.path match {
          case Uri.Path.Slash(Uri.Path.Segment(service, Uri.Path.Slash(Uri.Path.Segment(method, Uri.Path.Empty)))) =>
            val result =
              try chain(service, method, request)
              catch { case NonFatal(e) => Future.failed(e) }
            result.recover { case e => errorMapper.errorResponse(request, e) }(ExecutionContext.parasitic)
          case path @ Uri.Path.Slash(Uri.Path.Segment(_, Uri.Path.Slash(_))) =>
            Future.successful(
              errorMapper.errorResponse(
                request,
                new GrpcServiceException(
                  Status.INVALID_ARGUMENT.withDescription(s"Invalid gRPC request path [$path]"))))
          case _ => handler(request)
        }
    }
  }
}
