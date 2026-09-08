/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.GreeterService
import example.myapp.helloworld.grpc.GreeterServiceClient
import example.myapp.helloworld.grpc.GreeterServiceHandler
import example.myapp.helloworld.grpc.HelloReply
import example.myapp.helloworld.grpc.HelloRequest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * A Netty-backed akka-grpc client can get permanently wedged in CONNECTING if the peer accepts
 * the TCP connection but never completes the HTTP/2 handshake: grpc-java has no timeout for that
 * state, so the channel never reaches TRANSIENT_FAILURE and the client never retries on its own -
 * even once the peer becomes healthy again.
 */
class StuckConnectingReproducerSpec
    extends ScalaTestWithActorTestKit("""
        akka.http.server.enable-http2 = true
        """)
    with AnyWordSpecLike
    with Matchers {

  private val workingService: GreeterService = new GreeterService {
    override def sayHello(in: HelloRequest): Future[HelloReply] =
      Future.successful(HelloReply(message = s"hello, ${in.name}"))
    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???
    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???
  }

  /**
   * A raw TCP listener that accepts connections and then does nothing: no TLS, no HTTP/2 preface
   * ack. This simulates a black-holed connection: the TCP handshake completes, but nothing above
   * it ever will. Accepted sockets are held (not closed) so the connection doesn't get an RST,
   * which would surface as a normal, recoverable TRANSIENT_FAILURE.
   */
  private def startHangingServer(): (ServerSocket, CopyOnWriteArrayList[Socket]) = {
    val serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val heldSockets = new CopyOnWriteArrayList[Socket]()
    val acceptorThread = new Thread(() => {
      while (!serverSocket.isClosed) {
        try {
          heldSockets.add(serverSocket.accept())
        } catch {
          case _: Exception => // server socket closed, thread exiting
        }
      }
    })
    acceptorThread.setDaemon(true)
    acceptorThread.start()
    (serverSocket, heldSockets)
  }

  /** Repeatedly calls `sayHello` until one succeeds, or `within` elapses. */
  private def pollForSuccess(client: GreeterService, within: FiniteDuration)(implicit ec: ExecutionContext): Boolean = {
    val deadline = System.nanoTime() + within.toNanos
    var succeeded = false
    while (!succeeded && System.nanoTime() < deadline) {
      Try(Await.result(client.sayHello(HelloRequest(name = "ping")), 500.millis)) match {
        case Success(_) => succeeded = true
        case Failure(_) => // keep polling
      }
    }
    succeeded
  }

  /**
   * Runs the shared scenario: a client is pointed at a peer that is a black hole for 1s, then a
   * real, healthy server is bound on the exact same port. Returns whether a call to that client
   * ever succeeded within `within` after the swap.
   */
  private def runSwapScenario(connectingTimeout: Duration, within: FiniteDuration): Boolean = {
    implicit val ec: ExecutionContext = system.executionContext
    val (serverSocket, heldSockets) = startHangingServer()
    val port = serverSocket.getLocalPort
    var binding: Option[Http.ServerBinding] = None
    try {
      val settings = GrpcClientSettings
        .connectToServiceAt("127.0.0.1", port)
        .withTls(false)
        .withDeadline(500.millis)
        .withConnectingTimeout(connectingTimeout)
        // Kick off the first connection attempt immediately, without needing a call.
        .withEagerConnection(true)

      val client = GreeterServiceClient(settings)

      // Give the client a second to get stuck on the black hole, then swap in a real server on
      // the exact same port - the fix should notice on its own, nothing re-creates the client.
      Thread.sleep(1000)
      serverSocket.close()
      binding = Some(Http().newServerAt("127.0.0.1", port).bind(GreeterServiceHandler(workingService)).futureValue)

      pollForSuccess(client, within)
    } finally {
      binding.foreach(_.unbind())
      if (!serverSocket.isClosed) serverSocket.close()
      heldSockets.forEach(_.close())
    }
  }

  "A Netty-backed akka-grpc client whose peer is initially a black hole and later becomes healthy" should {

    "NOT recover on its own when connecting-timeout is disabled" in {
      runSwapScenario(connectingTimeout = Duration.Inf, within = 3.seconds) shouldBe false
    }

    "recover and complete a call on its own when connecting-timeout is enabled (the fix)" in {
      runSwapScenario(connectingTimeout = 300.millis, within = 3.seconds) shouldBe true
    }
  }
}
