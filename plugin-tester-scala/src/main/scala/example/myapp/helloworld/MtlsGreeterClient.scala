/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-client
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.common.SSLContextFactory
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.HelloRequest

import java.nio.file.Paths
import javax.net.ssl.SSLContext
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object MtlsGreeterClient {

  def main(args: Array[String]): Unit = {
    implicit val sys: ActorSystem = ActorSystem.create("MtlsHelloWorldClient")
    implicit val ec: ExecutionContext = sys.dispatcher

    val clientSettings = GrpcClientSettings.connectToServiceAt("localhost", 8443).withSslContext(sslContext())

    // alternatively, for rotating certs
    val rotatingClientSettings =
      GrpcClientSettings.connectToServiceAt("localhost", 8443).withSslContextProvider(rotatingSslContext())

    val client = GreeterServiceClient(rotatingClientSettings)

    while (true) {
      val reply = client.sayHello(HelloRequest("Jonas"))

      val tryResponse = Try { Await.result(reply, 5.seconds) }
      tryResponse match {
        case Success(reply) =>
          println(s"Successful reply: $reply")
        case Failure(exception) =>
          println("Request failed")
          exception.printStackTrace()
      }

      Thread.sleep(10000)
    }
  }

  def sslContext(): SSLContext = {
    SSLContextFactory.createSSLContextFromPem(
      // Note: these are filesystem paths, not classpath
      certificatePath = Paths.get("src/main/resources/certs/client1.crt"),
      privateKeyPath = Paths.get("src/main/resources/certs/client1.key"),
      // server cert is issued by this CA
      trustedCaCertificatePaths = Seq(Paths.get("src/main/resources/certs/rootCA.crt")))
  }

  def rotatingSslContext(): () => SSLContext = {
    SSLContextFactory.refreshingSSLContextProvider(30.seconds) { () =>
      println("### reloading cert")
      SSLContextFactory.createSSLContextFromPem(
        // Note: these are filesystem paths, not classpath
        Paths.get("src/main/resources/certs/client1.crt"),
        Paths.get("src/main/resources/certs/client1.key"),
        // server cert is issued by this CA
        Seq(Paths.get("src/main/resources/certs/rootCA.crt")))
    }
  }

}
//#full-client
