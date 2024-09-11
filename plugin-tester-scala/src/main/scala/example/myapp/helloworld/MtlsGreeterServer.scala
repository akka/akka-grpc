/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld

import akka.actor.ActorSystem
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.common.SSLContextFactory
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import example.myapp.helloworld.grpc._
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.Source

object MtlsGreeterServer {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("MtlsHelloWorldServer")
    new MtlsGreeterServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

}

class MtlsGreeterServer(system: ActorSystem) {

  private val log = LoggerFactory.getLogger(classOf[MtlsGreeterServer])
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      GreeterServiceHandler(new GreeterServiceImpl())

    // Bind service handler servers to localhost:8443
    val binding =
      Http()
        .newServerAt("127.0.0.1", 8443)
        .enableHttps(serverHttpContext)
        .bind(service)
        .map(_.addToCoordinatedShutdown(5.seconds))

    // report successful binding
    binding.foreach { binding => log.info(s"gRPC server bound to: {}", binding.localAddress) }

    binding
  }

  private def serverHttpContext: HttpsConnectionContext =
    ConnectionContext.httpsServer { () =>
      val context = SSLContextFactory.createSSLContextFromPem(
        // Note: these are filesystem paths, not classpath
        certificatePath = Paths.get("src/main/resources/certs/localhost-server.crt"),
        privateKeyPath = Paths.get("src/main/resources/certs/localhost-server.key"),
        // client certs are issued by this CA
        trustedCaCertificatePaths = Seq(Paths.get("src/main/resources/certs/rootCA.crt")))

      val engine = context.createSSLEngine()
      engine.setUseClientMode(false)

      // require client certs
      engine.setNeedClientAuth(true)

      engine
    }

  private def classPathFileAsString(path: String): String =
    Source.fromResource(path).mkString
}
//#full-server
