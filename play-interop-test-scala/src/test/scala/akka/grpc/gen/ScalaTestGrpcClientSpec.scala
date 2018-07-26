package akka.grpc.gen

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{ AkkaGrpcClient, AkkaGrpcClientFactory }
import akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatestplus.play.NewServerProvider
import play.api.test.ServerEndpoint

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }

trait GrpcClientSpec { this: NewServerProvider =>

  protected def grpcClientSettings: GrpcClientSettings = {
    val sslEndpoint = serverEndpoints.httpsEndpoint.get
    val as = grpcClientActorSystem
    GrpcClientSettings.fromConfig(grpcClientConfig(sslEndpoint, as.settings.config))(as)
      .withSSLContext(sslEndpoint.ssl.get.sslContext)
  }

  protected def grpcClientConfig(httpsEndpoint: ServerEndpoint, baseConfig: Config): Config = {
    ConfigFactory.empty()
      .withValue("host", ConfigValueFactory.fromAnyRef(httpsEndpoint.host))
      .withValue("port", ConfigValueFactory.fromAnyRef(httpsEndpoint.port))
      .withFallback(baseConfig.getConfig("akka.grpc.client.\"*\""))
  }
  protected def grpcClientActorSystem: ActorSystem = app.actorSystem
  protected def grpcClientMaterializer: Materializer = app.materializer
  protected def grpcClientExecutionContext: ExecutionContext = app.actorSystem.dispatcher
  protected def grpcClientCloseTimeout: Duration = Duration.Inf

  protected def grpcClient[T <: AkkaGrpcClient](implicit factory: AkkaGrpcClientFactory[T]): T =
    factory(grpcClientSettings)(grpcClientMaterializer, grpcClientExecutionContext)

  /**
   * Runs a block of code with a gRPC client, closing the client afterwards.
   * @tparam T The type of gRPC client.
   */
  trait WithGrpcClient[T <: AkkaGrpcClient] {
    def apply[U](f: T => U)(implicit factory: AkkaGrpcClientFactory[T])
  }

  protected def withGrpcClient[T <: AkkaGrpcClient]: WithGrpcClient[T] = new WithGrpcClient[T] {
    override def apply[U](f: T => U)(implicit factory: AkkaGrpcClientFactory[T]): Unit = {
      val client = grpcClient[T]
      try client finally Await.result(client.close(), grpcClientCloseTimeout)
    }
  }
}

