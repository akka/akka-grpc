package akka.grpc.javadsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.{ HttpTranscoding => InternalTranscoding }
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.japi.function.{ Function => JFunction }
import akka.stream.Materializer

import java.util.concurrent.CompletionStage

object HttpTranscoding {

  @ApiMayChange
  def partial(
      serviceDescription: ServiceDescription,
      grpcHandler: JFunction[HttpRequest, CompletionStage[HttpResponse]],
      mat: Materializer,
      system: ClassicActorSystemProvider): JFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val fileDescriptor = serviceDescription.descriptor
    val handlers = InternalTranscoding.serve(fileDescriptor)(mat, system.classicSystem.dispatcher).map {
      case (method, binding) => new HttpHandler(method, binding, grpcHandler)(mat, system.classicSystem.dispatcher)
    }

    ServiceHandler.concat(handlers: _*)
  }
}
