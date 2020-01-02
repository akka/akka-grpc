/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop

import java.io.FileInputStream
import java.nio.file.{ Files, Paths }
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{ KeyFactory, KeyStore, SecureRandom }
import java.util.Base64

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.{ Http2, HttpsConnectionContext }
import akka.stream.{ ActorMaterializer, Materializer }
import io.grpc.internal.testing.TestUtils
import javax.net.ssl.{ KeyManagerFactory, SSLContext }

import scala.concurrent.{ Await, Future }

/**
 * Glue code to start a gRPC server based on the akka-grpc Scala API to test against
 */
case class AkkaGrpcServerScala(serverHandlerFactory: Materializer => ActorSystem => HttpRequest => Future[HttpResponse])
    extends GrpcServer[(ActorSystem, ServerBinding)] {
  override def start() = {
    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = sys.dispatcher

    val testService = serverHandlerFactory(mat)(sys)

    val bindingFuture = Http2().bindAndHandleAsync(
      testService,
      interface = "127.0.0.1",
      port = 0,
      parallelism = 256, // TODO remove once https://github.com/akka/akka-http/pull/2146 is merged
      connectionContext = serverHttpContext())

    val binding = Await.result(bindingFuture, 10.seconds)
    (sys, binding)
  }

  override def stop(binding: (ActorSystem, ServerBinding)) = binding match {
    case (sys, binding) =>
      sys.log.info("Exception thrown, unbinding")
      Await.result(binding.unbind(), 10.seconds)
      Await.result(sys.terminate(), 10.seconds)
  }

  private def serverHttpContext() = {
    val keyEncoded =
      new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath)), "UTF-8")
        .replace("-----BEGIN PRIVATE KEY-----\n", "")
        .replace("-----END PRIVATE KEY-----\n", "")
        .replace("\n", "")

    val decodedKey = Base64.getDecoder.decode(keyEncoded)

    val spec = new PKCS8EncodedKeySpec(decodedKey)

    val kf = KeyFactory.getInstance("RSA")
    val privateKey = kf.generatePrivate(spec)

    val fact = CertificateFactory.getInstance("X.509")
    val is = new FileInputStream(TestUtils.loadCert("server1.pem"))
    val cer = fact.generateCertificate(is)

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null)
    ks.setKeyEntry("private", privateKey, Array.empty, Array(cer))

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, null)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    new HttpsConnectionContext(context)
  }

  override def getPort(binding: (ActorSystem, ServerBinding)): Int = binding._2.localAddress.getPort
}
