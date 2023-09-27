package akka.grpc.javadsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.HttpTranscoding
import akka.http.javadsl.{ model => jm }
import akka.http.scaladsl.{ model => sm }
import akka.japi.function.{ Function => JFunction }
import akka.stream.Materializer

import java.util.concurrent.CompletionStage
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

object HttpHandler {
  import scala.compat.java8.FutureConverters._

  private def adaptJFunction(f: JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]])(
      implicit ec: ExecutionContext): PartialFunction[sm.HttpRequest, Future[sm.HttpResponse]] = {
    Function.unlift { request =>
      // TODO is this safe? since it ignores returned notFound
      Some(f.apply(request).toScala.map(response => response.asInstanceOf[sm.HttpResponse]))
    }
  }

  private def adaptSFunction(f: PartialFunction[sm.HttpRequest, Future[sm.HttpResponse]])(
      implicit ec: ExecutionContext): JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] = {
    japiFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] { request =>
      f.lift
        .apply(request.asInstanceOf[sm.HttpRequest])
        .map(_.map(_.asInstanceOf[jm.HttpResponse]).toJava)
        .getOrElse(ServiceHandler.notFound)
    }
  }

  @ApiMayChange
  def partial(
      serviceDescription: ServiceDescription,
      grpcHandler: JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]],
      mat: Materializer,
      system: ClassicActorSystemProvider): JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] = {
    implicit val ec: ExecutionContextExecutor = system.classicSystem.dispatcher
    implicit val mati: Materializer = mat
    val fileDescriptor = serviceDescription.descriptor
    val sGrpcHandler = adaptJFunction(grpcHandler)
    val handlers = HttpTranscoding.parseRules(fileDescriptor).map {
      case (method, binding) =>
        val sHttpHandler = HttpTranscoding.httpHandler(method, binding, sGrpcHandler)
        adaptSFunction(sHttpHandler)
    }

    ServiceHandler.concat(handlers: _*)
  }
}
