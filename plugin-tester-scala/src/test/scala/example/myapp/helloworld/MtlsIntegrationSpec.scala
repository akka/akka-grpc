/*
 * Copyright (C) 2023-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.SSLContextFactory
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import scala.concurrent.Future

class MtlsIntegrationSpec
    extends ScalaTestWithActorTestKit("akka.http.server.enable-http2 = true")
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private var serverBinding: Option[Http.ServerBinding] = None

  override protected def beforeAll(): Unit = {
    val server = new MtlsGreeterServer(system.classicSystem)
    serverBinding = Some(server.run().futureValue)
  }

  "A mTLS server and client" should {

    "be able to talk" in {
      val clientSettings =
        GrpcClientSettings.connectToServiceAt("localhost", 8443).withSslContext(MtlsGreeterClient.sslContext())

      val client = GreeterServiceClient(clientSettings)

      client.sayHello(HelloRequest("Jonas")).futureValue
    }

    "not allow an unknown client to talk" in {
      val clientSettings =
        GrpcClientSettings
          .connectToServiceAt("localhost", 8443)
          .withSslContext(
            SSLContextFactory.createSSLContextFromPem(
              // Note: these are filesystem paths, not classpath
              certificatePath = Paths.get("src/main/resources/certs/bad-client.crt"),
              privateKeyPath = Paths.get("src/main/resources/certs/bad-client.key"),
              // server cert is issued by this CA
              trustedCaCertificatePaths = Seq(Paths.get("src/main/resources/certs/rootCA.crt"))))

      val client = GreeterServiceClient(clientSettings)

      client.sayHello(HelloRequest("Jonas")).failed.futureValue
    }

    "be able to rotate certificates" in {
      val refreshProbe = createTestProbe[String]()
      val certRefreshed = "Cert refreshed!"

      @volatile var certCounter = 0
      def nextContext(): SSLContext = {
        certCounter = math.min(2, certCounter + 1)
        SSLContextFactory.createSSLContextFromPem(
          // Note: these are filesystem paths, not classpath
          certificatePath = Paths.get(s"src/main/resources/certs/client$certCounter.crt"),
          privateKeyPath = Paths.get(s"src/main/resources/certs/client$certCounter.key"),
          // server cert is issued by this CA
          trustedCaCertificatePaths = Seq(Paths.get("src/main/resources/certs/rootCA.crt")))
      }

      val clientSettings =
        GrpcClientSettings
          .connectToServiceAt("localhost", 8443)
          .withSslContextProvider { () =>
            refreshProbe.ref ! certRefreshed
            nextContext()
          }
          // to make sure the connection is closed quickly and cert is refreshed
          .withChannelBuilderOverrides(_.idleTimeout(2, TimeUnit.SECONDS))

      val clientFuture = Future { GreeterServiceClient(clientSettings) }(system.executionContext)
      // initial load on client create
      refreshProbe.expectMessage(certRefreshed)
      val client = clientFuture.futureValue

      val reply1 = client.sayHello(HelloRequest("Jonas"))
      reply1.futureValue

      // connection idles and is closed
      Thread.sleep(3)

      val reply2 = client.sayHello(HelloRequest("Jonas"))
      // refresh, once next request is done (at least one, possibly more, timing sensitive, but that is fine)
      refreshProbe.expectMessage(certRefreshed)
      // refreshed to client2 keypair
      reply2.futureValue

    }

  }
}
