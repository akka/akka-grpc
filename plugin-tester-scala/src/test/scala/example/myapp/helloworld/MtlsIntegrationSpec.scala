package example.myapp.helloworld

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import example.myapp.helloworld.grpc.{ GreeterServiceClient, HelloRequest }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MtlsIntegrationSpec
    extends ScalaTestWithActorTestKit("akka.http.server.enable-http2 = true")
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  "A mTLS server and client" should {

    "be able to talk" in {
      val server = new MtlsGreeterServer(system.classicSystem)
      val serverBinding = server.run().futureValue

      try {
        val clientSettings =
          GrpcClientSettings.connectToServiceAt("localhost", 8443).withSslContext(MtlsGreeterClient.sslContext())

        val client = GreeterServiceClient(clientSettings)

        client.sayHello(HelloRequest("Jonas")).futureValue

      } finally {
        serverBinding.unbind().futureValue
      }
    }

  }
}
