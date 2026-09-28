/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.annotation.InternalApi
import akka.grpc.GrpcProtocol
import akka.grpc.Trailers
import akka.grpc.scaladsl.ServerInterceptor
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import io.grpc.Status

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[grpc] object ServerInterceptorSupport {
  type Handler = HttpRequest => Future[HttpResponse]
  private type Chain = (String, String, HttpRequest) => Future[HttpResponse]

  private val unsupportedMediaType = HttpResponse(StatusCodes.UnsupportedMediaType)

  def serviceName(path: Uri.Path): Option[String] =
    path match {
      case Uri.Path.Slash(Uri.Path.Segment(service, Uri.Path.Slash(_))) => Some(service)
      case _                                                            => None
    }

  /**
   * Wraps the handler so that requests with a `/service/method` path go through the interceptors. Any failure is
   * turned into a gRPC error response with `errorMapper`. Other paths under a service are rejected with
   * `INVALID_ARGUMENT`, like the generated handlers do. Paths that do not look like a service call are passed to the
   * handler untouched.
   */
  def intercept(handler: Handler, interceptors: Seq[ServerInterceptor], errorMapper: Throwable => Trailers): Handler = {
    if (interceptors.isEmpty) handler
    else {
      // every step is guarded so that outer interceptors see a synchronous throw as a failed future
      val chain = interceptors.foldRight[Chain]((_, _, request) => handler(request)) {
        (interceptor, next) => (service, method, request) =>
          interceptor.intercept(service, method, request, r => safely(next(service, method, r)))
      }

      request =>
        request.uri.path match {
          case Uri.Path.Slash(Uri.Path.Segment(service, Uri.Path.Slash(Uri.Path.Segment(method, Uri.Path.Empty)))) =>
            safely(chain(service, method, request)).recover {
              case e => errorResponse(request, errorMapper(e))
            }(ExecutionContext.parasitic)
          case path if serviceName(path).isDefined =>
            Future.successful(
              errorResponse(
                request,
                Trailers(Status.INVALID_ARGUMENT.withDescription(s"Invalid gRPC request path [$path]"))))
          case _ => handler(request)
        }
    }
  }

  private def safely(call: => Future[HttpResponse]): Future[HttpResponse] =
    try call
    catch { case NonFatal(e) => Future.failed(e) }

  private def errorResponse(request: HttpRequest, trailers: Trailers): HttpResponse =
    GrpcProtocol.negotiate(request) match {
      case Some((_, writer)) => GrpcResponseHelpers.status(trailers)(writer)
      case None              => unsupportedMediaType
    }
}
