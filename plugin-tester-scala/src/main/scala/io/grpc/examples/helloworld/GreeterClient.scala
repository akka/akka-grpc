//#full-client
package io.grpc.examples.helloworld

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import io.grpc.CallOptions
import io.grpc.StatusRuntimeException
import io.grpc.internal.testing.TestUtils
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

object GreeterClient {

  def main(args: Array[String]): Unit = {

    val serverHost = "127.0.0.1"
    val serverPort = 8080
    val useTls = true
    val useTestCa = true
    val serverHostOverride: String = "foo.test.google.fr"

    val sslContext: SslContext = {
      if (useTestCa) {
        try // FIXME issue #89
          GrpcSslContexts.forClient.trustManager(TestUtils.loadCert("ca.pem")).build()
        catch {
          case ex: Exception => throw new RuntimeException(ex)
        }
      } else null
    }

    val channelBuilder =
      NettyChannelBuilder
        .forAddress(serverHost, serverPort)
        .flowControlWindow(65 * 1024)
        .negotiationType(if (useTls) NegotiationType.TLS else NegotiationType.PLAINTEXT)
        .sslContext(sslContext)

    if (useTls && serverHostOverride != null)
      channelBuilder.overrideAuthority(serverHostOverride)

    val channel = channelBuilder.build()
    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = sys.dispatcher
    try {

      val callOptions = CallOptions.DEFAULT

      val client = new GreeterServiceClient(channel, callOptions)

      def singleRequestReply(): Unit = {
        val reply = client.sayHello(HelloRequest("Alice"))
        println(s"got single reply: ${Await.result(reply, 5.seconds).message}")
      }

      def streamingRequest(): Unit = {
        val requests = List("Alice", "Bob", "Peter").map(HelloRequest.apply)
        val reply = client.itKeepsTalking(Source(requests))
        println(s"got single reply for streaming requests: ${Await.result(reply, 5.seconds).message}")
      }


      def streamingReply(): Unit = {
        val responseStream = client.itKeepsReplying(HelloRequest("Alice"))
        val done: Future[Done] =
          responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
        Await.ready(done, 1.minute)
      }

      def streamingRequestReply(): Unit = {
        val requestStream: Source[HelloRequest, _] =
          Source
            .tick(100.millis, 1.second, "tick")
            .zipWithIndex
            .map { case (_, i) => i }
            .map(i => HelloRequest(s"Alice-$i"))
            .take(10)
        // FIXME mat value of streamHellos is Any?
        val responseStream: Source[HelloReply, _] = client.streamHellos(requestStream)
        val done: Future[Done] =
          responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
        Await.ready(done, 1.minute)
      }

      singleRequestReply()
      streamingRequest()
      streamingReply()
      streamingRequestReply()

    } catch {
      case e: StatusRuntimeException =>
        println(s"Status: ${e.getStatus}")
      case NonFatal(e) =>
        println(e)
    } finally {
      Try(channel.shutdown().awaitTermination(10, TimeUnit.SECONDS))
      sys.terminate()
    }

  }

}
//#full-client
