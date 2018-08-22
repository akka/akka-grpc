/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{AkkaGrpcClient, AkkaGrpcClientFactory}
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import javax.net.ssl.SSLContext
import play.api.Application
import play.api.test.{ServerEndpoint, ServerEndpoints}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

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
    factory()

  /** The close timeout used by gRPC clients. */
  protected def grpcClientCloseTimeout: Duration = Duration(30, TimeUnit.SECONDS)


}

object AkkaGrpcClientHelpers {
  /**
   * Configure a factory from an application and some server endpoints. Expects to have exactly one HTTP/2 endpoint.
   */
  def factoryForAppEndpoints[T <: AkkaGrpcClient](app: Application, serverEndpoints: ServerEndpoints)(implicit factory: AkkaGrpcClientFactory[T]): AkkaGrpcClientFactory.Configured[T] = {
    val possibleEndpoints = serverEndpoints.endpoints.filter(e => e.scheme == "https" && e.httpVersions.contains("2"))
    if (possibleEndpoints.size != 1) { throw new IllegalArgumentException(s"gRPC client can't automatically find HTTP/2 connection: ${possibleEndpoints.size} valid endpoints available: ${serverEndpoints}") }
    factoryForAppEndpoints(app, possibleEndpoints.head)
  }

  /**
   * Configure a factory from an application and a server endpoints.
   */
  def factoryForAppEndpoints[T <: AkkaGrpcClient](app: Application, serverEndpoint: ServerEndpoint)(implicit factory: AkkaGrpcClientFactory[T]): AkkaGrpcClientFactory.Configured[T] = {
    val config: Config = app.configuration.underlying
    implicit val sys: ActorSystem = app.actorSystem
    val materializer: Materializer = app.materializer
    val executionContext: ExecutionContext = sys.dispatcher

    val clientConfig = ConfigFactory.empty() // TODO: Could load the client config by name
      .withValue("host", ConfigValueFactory.fromAnyRef(serverEndpoint.host))
      .withValue("port", ConfigValueFactory.fromAnyRef(serverEndpoint.port))
      .withFallback(sys.settings.config.getConfig("akka.grpc.client.\"*\""))
    val sslContext: SSLContext = serverEndpoint.ssl.get.sslContext

    val settings = GrpcClientSettings.fromConfig(clientConfig).withSSLContext(sslContext)
    AkkaGrpcClientFactory.configure[T](settings, materializer, executionContext)
  }
}