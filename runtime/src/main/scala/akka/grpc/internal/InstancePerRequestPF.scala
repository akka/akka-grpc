/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalStableApi
import akka.grpc.Trailers
import akka.http.scaladsl.model
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.SystemMaterializer

import java.util.concurrent.CompletionStage
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

/**
 * INTERNAL API
 *
 * Internal abstraction for Akka gRPC integration in Akka SDK. Used only by the generated ScalaHandlerFactory for
 * Java gRPC service bootstrap when the codegen option generateScalaHandlerFactory is set.
 */
@InternalStableApi
private[akka] object InstancePerRequestPF {
  // instantiated by generated code so must be kept binary compatible.
  final class GrpcMethod[S](
      val name: String,
      val handle: (
          // service instance
          S,
          // exception handler
          akka.japi.Function[akka.actor.ActorSystem, akka.japi.Function[java.lang.Throwable, akka.grpc.Trailers]],
          Materializer,
          ClassicActorSystemProvider,
          akka.http.javadsl.model.HttpRequest) => CompletionStage[akka.http.javadsl.model.HttpResponse])
}

/**
 * INTERNAL API
 */
// instantiated and called by generated code so must be kept binary compatible.
@InternalStableApi
private[akka] final class InstancePerRequestPF[S](
    factory: HttpRequest => S,
    prefix: String,
    methods: Array[InstancePerRequestPF.GrpcMethod[S]],
    eHandler: PartialFunction[Throwable, Trailers],
    system: ClassicActorSystemProvider)
    extends PartialFunction[HttpRequest, Future[HttpResponse]] {
  // Note how the factory and partial function is Akka HTTP scaladsl. This is intentional.

  private val materializer: Materializer = SystemMaterializer(system).materializer
  private implicit val ec: ExecutionContext = materializer.executionContext
  private val spi = TelemetryExtension(system).spi
  private val javaEHandler: akka.japi.Function[ActorSystem, akka.japi.Function[Throwable, Trailers]] =
    (_: ActorSystem) => { eHandler.apply _ }

  private val methodByName: Map[String, InstancePerRequestPF.GrpcMethod[S]] = methods.map(m => m.name -> m).toMap

  def isThisService(path: Uri.Path): Boolean =
    path match {
      case Uri.Path.Slash(Uri.Path.Segment(`prefix`, Uri.Path.Slash(_))) => true
      case _                                                             => false
    }

  override def isDefinedAt(request: HttpRequest): Boolean =
    // FINE with only service name match, unknown methods are invalid requests below
    isThisService(request.uri.path)

  override def apply(request: HttpRequest): Future[HttpResponse] = {
    request.uri.path.tail.tail match {
      case model.Uri.Path.Slash(model.Uri.Path.Segment(method, model.Uri.Path.Empty)) =>
        handle(spi.onRequest(prefix, method, request), method)
      case _ =>
        scala.concurrent.Future.failed(
          new akka.grpc.GrpcServiceException(
            io.grpc.Status.INVALID_ARGUMENT.withDescription(s"Invalid gRPC request path [${request.uri.path}]")))
    }
  }

  private def handle(request: HttpRequest, method: String): scala.concurrent.Future[HttpResponse] =
    methodByName.get(method) match {
      case Some(grpcMethod) =>
        val implementation = factory(request)
        grpcMethod
          .handle(implementation, javaEHandler, materializer, system, request)
          .asScala
          .asInstanceOf[Future[HttpResponse]] // HTTP scaladsl response is always javadsl
      case None =>
        scala.concurrent.Future.failed(new NotImplementedError(s"Not implemented: $method"))
    }

}
