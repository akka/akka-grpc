/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.event.Logging
import akka.grpc.Trailers
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.Materializer
import com.google.rpc.LocalizedMessage
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.{ GreeterService, GreeterServiceHandler, HelloReply, HelloRequest }
import io.grpc.Status

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object LoggingErrorHandlingGreeterServer {
  def main(args: Array[String]): Unit = {
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("Server", conf)
    new LoggingErrorHandlingGreeterServer(system).run()
  }
}

class LoggingErrorHandlingGreeterServer(system: ActorSystem) {
  //#implementation

  private val nameNonEmptyException = new IllegalArgumentException("Name must be non-empty")
  private val nameCapitalizedException = new IllegalArgumentException("Name must be capitalized")

  private final class Impl(mat: Materializer) extends GreeterServiceImpl()(mat) {
    override def sayHello(in: HelloRequest): Future[HelloReply] =
      if (in.name.isEmpty) {
        Future.failed(nameNonEmptyException)
      } else if (in.name.head.isLower) {
        Future.failed(nameCapitalizedException)
      } else {
        Future.successful(HelloReply(s"Hello, ${in.name}"))
      }
  }
  //#implementation

  //#method
  private type ErrorHandler = ActorSystem => PartialFunction[Throwable, Trailers]

  private def loggingErrorHandlingGrpcRoute[ServiceImpl](
      buildImpl: RequestContext => ServiceImpl,
      errorHandler: ErrorHandler,
      buildHandler: (ServiceImpl, ErrorHandler) => HttpRequest => Future[HttpResponse]): Route =
    DebuggingDirectives.logRequestResult(("loggingErrorHandlingGrpcRoute", Logging.InfoLevel)) {
      extractRequestContext { ctx =>
        val loggingErrorHandler: ErrorHandler = (sys: ActorSystem) => {
          case NonFatal(t) =>
            val pf = errorHandler(sys)
            if (pf.isDefinedAt(t)) {
              val trailers: Trailers = pf(t)
              ctx.log.error(t, s"Grpc failure handled and mapped to $trailers")
              trailers
            } else {
              val trailers = Trailers(Status.INTERNAL)
              ctx.log.error(t, s"Grpc failure UNHANDLED and mapped to $trailers")
              trailers
            }
        }
        val impl = buildImpl(ctx)
        val handler = buildHandler(impl, loggingErrorHandler)
        handle(handler)
      }
    }
  //#method

  //#custom-error-mapping
  private val customErrorMapping: PartialFunction[Throwable, Trailers] = {
    case ex: IllegalArgumentException =>
      if (ex.getMessage == nameNonEmptyException.getMessage) {
        // We can pass through the message by attaching it to the Status.
        Trailers(Status.INVALID_ARGUMENT.withDescription(ex.getMessage))
      } else if (ex.getMessage == nameCapitalizedException.getMessage) {
        // We can pass through extra error details like localized versions of the message.
        Trailers(com.google.rpc.Code.INVALID_ARGUMENT, ex.getMessage, List(LocalizedMessage("en-US", ex.getMessage)))
      } else {
        // If we don't recognize the exception, we might want to withhold the message.
        Trailers(Status.INVALID_ARGUMENT)
      }
  }
  //#custom-error-mapping

  def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    //#combined
    val route = loggingErrorHandlingGrpcRoute[GreeterService](
      buildImpl = rc => new Impl(rc.materializer),
      buildHandler = (impl, eHandler) =>
        ServiceHandler.concatOrNotFound(
          GreeterServiceHandler.partial(impl, eHandler = eHandler),
          ServerReflection.partial(List(GreeterService))),
      errorHandler = _ => customErrorMapping)

    // Bind service handler servers to localhost:8082
    val binding = Http().newServerAt("127.0.0.1", 8082).bind(route)
    //#combined

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
