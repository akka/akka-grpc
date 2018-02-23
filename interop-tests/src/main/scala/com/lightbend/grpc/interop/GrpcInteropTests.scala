package com.lightbend.grpc.interop

import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import java.security.{KeyFactory, KeyStore, SecureRandom}
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http2, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, Materializer}
import io.grpc.StatusRuntimeException
import io.grpc.internal.testing.TestUtils
import io.grpc.testing.integration.Util
import io.grpc.testing.integration2.{TestServiceClient, TestServiceServer}
import org.scalatest.{Assertion, Succeeded, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

trait GrpcInteropTests { self: WordSpec =>
  import org.scalatest.Matchers._

  // see https://github.com/grpc/grpc/blob/master/tools/run_tests/run_interop_tests.py#L543
  val testCases = Seq(
    "large_unary",
    "empty_unary",
    "ping_pong",
    "empty_stream",
    "client_streaming",
    "server_streaming",
    "cancel_after_begin",
    "cancel_after_first_response",
    "timeout_on_sleeping_server",
    "custom_metadata",
    "status_code_and_message",
    "unimplemented_method",
    "client_compressed_unary",
    "client_compressed_streaming",
    "server_compressed_unary",
    "server_compressed_streaming",
    "unimplemented_service",
  )

  val pendingJavaTestCases = Seq(
    "client_compressed_unary",
    "client_compressed_streaming"
  )

  val pendingAkkaTestCases: Seq[String]

  def javaGrpcTests() =
    "java grpc server" should {
      testCases.foreach { testCaseName =>
        s"pass the $testCaseName integration test" in {
          pendingTestCaseSupport(pendingJavaTestCases.contains(testCaseName)) {
            withGrpcJavaServer() {
              runGrcpJavaClient(testCaseName)
            }
          }
        }
      }
    }

  def akkaHttpGrpcTests(testServiceFactory: Materializer => ExecutionContext => PartialFunction[HttpRequest, Future[HttpResponse]]) =
    "akka-http grpc server" should {
      testCases.foreach { testCaseName =>
        s"pass the $testCaseName integration test" in {
          pendingTestCaseSupport(pendingAkkaTestCases.contains(testCaseName)) {
            withGrpcAkkaServer(testServiceFactory) {
              runGrcpJavaClient(testCaseName)
            }
          }
        }
      }
    }

  private def runGrcpJavaClient(testCaseName: String): Unit = {
    val args: Array[String] = Array("--server_host_override=foo.test.google.fr", "--use_test_ca=true", s"--test_case=$testCaseName")

    Util.installConscryptIfAvailable()
    val client = new TestServiceClient
    client.parseArgs(args)
    client.setUp()

    try
      client.run()
    finally
      client.tearDown()
  }

  private def pendingTestCaseSupport(expectedToFail: Boolean)(block: => Unit): Assertion = {
    val result = try {
      block
      Succeeded
    } catch {
      case e if expectedToFail => pending
    }

    result match {
      case Succeeded if expectedToFail => fail("Succeeded against expectations")
      case res => res
    }
  }

  private def withGrpcAkkaServer(testServiceFactory: Materializer => ExecutionContext => PartialFunction[HttpRequest, Future[HttpResponse]])(block: => Unit): Assertion = {
    implicit val sys = ActorSystem()
    try {
      implicit val mat = ActorMaterializer()

      val testService = testServiceFactory(mat)(sys.dispatcher)

      val bindingFuture = Http2().bindAndHandleAsync(
        testService.orElse { case _: HttpRequest ⇒ Future.successful(HttpResponse(StatusCodes.NotFound)) },
        interface = "127.0.0.1",
        port = 8080,
        httpsContext = serverHttpContext())

      val binding = Await.result(bindingFuture, 10.seconds)

      try {
        block
      } finally {
        sys.log.info("Exception thrown, unbinding")
        Await.result(binding.unbind(), 10.seconds)
        Await.result(sys.terminate(), 10.seconds)
      }
      Succeeded
    } catch {
      case e: StatusRuntimeException =>
        // 'Status' is not serializable, so we have to unpack the exception
        // to avoid trouble when running tests from sbt
        if (e.getCause == null) fail(e.getMessage)
        else fail(e.getMessage, e.getCause)
      case NonFatal(t) => fail(t)
    }
  }

  private def serverHttpContext() = {
    val keyEncoded = new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath)), "UTF-8")
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

  private def withGrpcJavaServer()(block: => Unit): Assertion = {
    try {
      val server = new TestServiceServer
      if (server.useTls)
        println("\nUsing fake CA for TLS certificate. Test clients should expect host\n" +
          "*.test.google.fr and our test CA. For the Java test client binary, use:\n" +
          "--server_host_override=foo.test.google.fr --use_test_ca=true\n")

      server.start()
      try
        block
      finally
        server.stop()
      Succeeded
    } catch {
      case NonFatal(t) => fail(t)
    }
  }
}
