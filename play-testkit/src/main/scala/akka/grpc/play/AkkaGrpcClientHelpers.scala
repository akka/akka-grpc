/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{ AkkaGrpcClient, AkkaGrpcClientFactory }
import akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import javax.net.ssl.SSLContext
import play.api.Application
import play.api.test.{ ServerEndpoint, ServerEndpoints }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }
import scala.reflect.ClassTag

/**
 * Helpers to test gRPC clients with Play. The methods in this class require
 * an implicit `AkkaGrpcClientFactory.Configured[T]` to be in scope. This can
 * usually be done by mixing in a method that knows how to configure the factory
 * for the current environment, e.g. by configuring the correct port values.
 */
trait AkkaGrpcClientHelpers {

  /**
   * Create a gRPC client to connect to the currently running test server.
   * @tparam T The type of client to create.
   * @return
   */
  def withGrpcClient[T <: AkkaGrpcClient]: WithGrpcClient[T] = new WithGrpcClient[T]

  /**
   * Runs a block of code with a gRPC client, closing the client afterwards.
   * @tparam T The type of gRPC client.
   */
  final class WithGrpcClient[T <: AkkaGrpcClient] {
    def apply[U](f: T => U)(implicit factory: AkkaGrpcClientFactory.Configured[T]): U = {
      val client = grpcClient[T]
      try f(client) finally Await.result(client.close(), grpcClientCloseTimeout)
    }
  }

  /**
   * Get a gRPC client to connect to the currently running test server. Remember
   * to close it afterwards, or use [[withGrpcClient]] to have it closed automatically.
   */
  def grpcClient[T <: AkkaGrpcClient](implicit factory: AkkaGrpcClientFactory.Configured[T]): T =
    factory.create()

  /** The close timeout used by gRPC clients. */
  protected def grpcClientCloseTimeout: Duration = Duration(30, TimeUnit.SECONDS)

}

object AkkaGrpcClientHelpers {
  /**
   * Configure a factory from an application and some server endpoints. Expects to have exactly one HTTP/2 endpoint.
   */
  def factoryForAppEndpoints[T <: AkkaGrpcClient: ClassTag](app: Application, serverEndpoints: ServerEndpoints): AkkaGrpcClientFactory.Configured[T] = {
    factoryForAppEndpoints(app, JavaAkkaGrpcClientHelpers.unsafeGetHttp2Endpoint(serverEndpoints))
  }

  /**
   * Configure a factory from an application and a server endpoints.
   */
  def factoryForAppEndpoints[T <: AkkaGrpcClient: ClassTag](app: Application, serverEndpoint: ServerEndpoint): AkkaGrpcClientFactory.Configured[T] = {
    implicit val sys: ActorSystem = app.actorSystem
    implicit val materializer: Materializer = app.materializer
    implicit val executionContext: ExecutionContext = sys.dispatcher
    AkkaGrpcClientFactory.configure[T](JavaAkkaGrpcClientHelpers.grpcClientSettings(serverEndpoint, sys))
  }
}
