/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalatestplus.play

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{ AkkaGrpcClient, AkkaGrpcClientFactory }
import akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatestplus.play.NewServerProvider
import play.api.test.ServerEndpoint

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }

/**
 * Helpers to test gRPC clients with Play using ScalaTest.
 */
trait ServerGrpcClient { this: NewServerProvider =>

  /**
   * Create a gRPC client to connect to the currently running test server.
   * @tparam T The type of client to create.
   * @return
   */
  protected def withGrpcClient[T <: AkkaGrpcClient]: WithGrpcClient[T] = new WithGrpcClient[T]

  /**
   * Runs a block of code with a gRPC client, closing the client afterwards.
   * @tparam T The type of gRPC client.
   */
  final class WithGrpcClient[T <: AkkaGrpcClient] {
    def apply[U](f: T => U)(implicit factory: AkkaGrpcClientFactory[T]): Unit = {
      val client = grpcClient[T]
      try client finally Await.result(client.close(), grpcClientCloseTimeout)
    }
  }

  /**
   * Get a gRPC client to connect to the currently running test server. Remember
   * to close it afterwards, or use [[withGrpcClient]] to have it closed automatically.
   */
  protected def grpcClient[T <: AkkaGrpcClient](implicit factory: AkkaGrpcClientFactory[T]): T =
    factory(grpcClientSettings)(grpcClientMaterializer, grpcClientExecutionContext)

  /**
   * Get the gRPC client settings to connect to the currently running test server.
   */
  protected def grpcClientSettings: GrpcClientSettings = {
    val sslEndpoint = serverEndpoints.httpsEndpoint.get
    val as = grpcClientActorSystem
    GrpcClientSettings.fromConfig(grpcClientConfig(sslEndpoint, as.settings.config))(as)
      .withSSLContext(sslEndpoint.ssl.get.sslContext)
  }

  /**
   * Get the gRPC client config needed to the currently running test server.
   */
  protected def grpcClientConfig(httpsEndpoint: ServerEndpoint, baseConfig: Config): Config = {
    ConfigFactory.empty()
      .withValue("host", ConfigValueFactory.fromAnyRef(httpsEndpoint.host))
      .withValue("port", ConfigValueFactory.fromAnyRef(httpsEndpoint.port))
      .withFallback(baseConfig.getConfig("akka.grpc.client.\"*\""))
  }

  /** The ActorSystem used by gRPC clients. */
  protected def grpcClientActorSystem: ActorSystem = app.actorSystem
  /** The Materializer used by gRPC clients. */
  protected def grpcClientMaterializer: Materializer = app.materializer
  /** The ExecutionContext used by gRPC clients. */
  protected def grpcClientExecutionContext: ExecutionContext = app.actorSystem.dispatcher
  /** The close timeout used by gRPC clients. */
  protected def grpcClientCloseTimeout: Duration = Duration(30, TimeUnit.SECONDS)
}

