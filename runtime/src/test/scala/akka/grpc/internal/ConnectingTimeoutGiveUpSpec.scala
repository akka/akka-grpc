/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.{ ByteArrayInputStream, InputStream }
import java.net.{ InetAddress, ServerSocket, Socket }
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ ConcurrentLinkedQueue, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.typesafe.config.ConfigFactory
import io.grpc.{ CallOptions, MethodDescriptor }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

object ConnectingTimeoutGiveUpSpec {
  sealed trait Mode
  // accept TCP but never send a byte: client stays CONNECTING (no TLS ServerHello / no HTTP/2 SETTINGS)
  case object Hang extends Mode
  // send an (empty) HTTP/2 SETTINGS frame so the grpc-java client transport becomes READY
  case object Http2 extends Mode

  /** Raw TCP server simulating an ingress that accepts connections but never completes the handshake. */
  final class FakeIngress(initial: Mode) extends AutoCloseable {
    val mode = new AtomicReference[Mode](initial)
    private val server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val port: Int = server.getLocalPort
    val accepted = new AtomicInteger(0)
    private val sockets = new ConcurrentLinkedQueue[Socket]()

    private val acceptor = new Thread(() => {
      while (!server.isClosed) {
        Try(server.accept()).foreach { s =>
          accepted.incrementAndGet()
          sockets.add(s)
          if (mode.get() == Http2) {
            // empty SETTINGS frame: length 0, type 4, flags 0, stream 0
            s.getOutputStream.write(Array[Byte](0, 0, 0, 4, 0, 0, 0, 0, 0))
            s.getOutputStream.flush()
          }
          val drainer = new Thread(() => {
            val buf = new Array[Byte](4096)
            Try { while (s.getInputStream.read(buf) >= 0) {} }
          })
          drainer.setDaemon(true)
          drainer.start()
        }
      }
    })
    acceptor.setDaemon(true)
    acceptor.start()

    def dropAllConnections(): Unit = sockets.asScala.foreach(s => Try(s.close()))

    override def close(): Unit = {
      Try(server.close())
      dropAllConnections()
    }
  }

  private val stringMarshaller = new MethodDescriptor.Marshaller[String] {
    override def stream(value: String): InputStream = new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
    override def parse(stream: InputStream): String = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
  }
  val dummyMethod: MethodDescriptor[String, String] = MethodDescriptor
    .newBuilder(stringMarshaller, stringMarshaller)
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName("test.Dummy/Call")
    .build()

  def unusedPort(): Int = {
    val s = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    val p = s.getLocalPort
    s.close()
    p
  }
}

/**
 * Connections abandoned by the connecting-timeout watchdog must not count towards connection-attempts,
 * and whenever akka-grpc does give up, the channel must be shut down as well.
 */
class ConnectingTimeoutGiveUpSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with Eventually
    with BeforeAndAfterAll {
  import ConnectingTimeoutGiveUpSpec._

  implicit val system: ActorSystem = ActorSystem(
    "ConnectingTimeoutGiveUpSpec",
    ConfigFactory.parseString("""akka.loglevel = DEBUG""").withFallback(ConfigFactory.load()))
  implicit val ec: scala.concurrent.ExecutionContext = system.dispatcher

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(50, Millis))

  private def settings(port: Int) =
    GrpcClientSettings
      .connectToServiceAt("127.0.0.1", port)
      .withTls(false)
      .withConnectionAttempts(3)
      .withConnectingTimeout(1.second)
      .withEagerConnection(true)

  private def call(channel: InternalChannel) =
    channel.invoke(
      "x",
      MetadataImpl.empty,
      dummyMethod,
      CallOptions.DEFAULT.withDeadlineAfter(150, TimeUnit.MILLISECONDS))

  // a channel stuck CONNECTING is abandoned by the watchdog after 1s, so a READY one stops opening new connections
  private def assertConnectionStable(ingress: FakeIngress): Unit = {
    eventually {
      val before = ingress.accepted.get
      Thread.sleep(2500)
      ingress.accepted.get shouldBe before
    }
  }

  "A Netty client channel with connecting-timeout" should {

    "keep retrying, not give up, when the handshake hangs and connect once the remote recovers (plaintext)" in {
      val ingress = new FakeIngress(Hang)
      try {
        val channel = NettyClientUtils.createChannel(settings(ingress.port), system.log)
        // well over connection-attempts x connecting-timeout
        Thread.sleep(6000)
        info(s"accepted connections while hanging: ${ingress.accepted.get}")
        channel.done.isCompleted shouldBe false
        // the watchdog keeps abandoning the stuck attempts
        ingress.accepted.get should be > 3

        ingress.mode.set(Http2)
        assertConnectionStable(ingress)
        channel.done.isCompleted shouldBe false
        channel.shutdown()
        channel.done.futureValue
      } finally ingress.close()
    }

    "keep retrying, not give up, when the TLS handshake hangs" in {
      val ingress = new FakeIngress(Hang)
      try {
        val channel = NettyClientUtils.createChannel(settings(ingress.port).withTls(true), system.log)
        Thread.sleep(6000)
        info(s"accepted connections while hanging: ${ingress.accepted.get}")
        channel.done.isCompleted shouldBe false
        ingress.accepted.get should be > 3
        channel.shutdown()
        channel.done.futureValue
      } finally ingress.close()
    }

    "hang in CONNECTING but never give up when connecting-timeout is infinite" in {
      val ingress = new FakeIngress(Hang)
      try {
        val channel =
          NettyClientUtils.createChannel(settings(ingress.port).withConnectingTimeout(Duration.Inf), system.log)
        Thread.sleep(4000)
        channel.done.isCompleted shouldBe false
        ingress.accepted.get shouldBe 1
        channel.shutdown()
        channel.done.futureValue
      } finally ingress.close()
    }

    "never give up against a refused port, sticky TRANSIENT_FAILURE is only counted once" in {
      val port = unusedPort()
      val channel = NettyClientUtils.createChannel(settings(port), system.log)
      // much longer than 3 x 1s connecting-timeout, and grpc-java keeps retrying with backoff meanwhile
      Thread.sleep(4000)
      channel.done.isCompleted shouldBe false
      channel.shutdown()
      channel.done.futureValue
    }

    "after having been READY, keep retrying hanging reconnects and recover once the remote does" in {
      val ingress = new FakeIngress(Http2)
      try {
        val channel = NettyClientUtils.createChannel(settings(ingress.port), system.log)
        eventually(ingress.accepted.get shouldBe 1)
        Thread.sleep(500) // let it reach READY

        // remote goes bad: drops connections and only hangs from now on
        ingress.mode.set(Hang)
        ingress.dropAllConnections()

        // keep making calls, like a projection would, so the channel keeps trying to reconnect
        val caller = system.scheduler.scheduleWithFixedDelay(Duration.Zero, 200.millis)(() => call(channel))
        try {
          Thread.sleep(6000)
          info(s"accepted connections while hanging: ${ingress.accepted.get}")
          channel.done.isCompleted shouldBe false
          ingress.accepted.get should be > 4

          ingress.mode.set(Http2)
          assertConnectionStable(ingress)
          channel.done.isCompleted shouldBe false
        } finally caller.cancel()
        channel.shutdown()
        channel.done.futureValue
      } finally ingress.close()
    }

    "after having been READY, shut the channel down when giving up" in {
      val ingress = new FakeIngress(Http2)
      val channel = NettyClientUtils.createChannel(settings(ingress.port).withConnectionAttempts(1), system.log)
      try {
        eventually(ingress.accepted.get shouldBe 1)
        Thread.sleep(500) // let it reach READY
      } finally ingress.close() // connections now refused
      // reconnect attempt is refused, which with connection-attempts = 1 means giving up.
      // Keep calling, a single call could race with the dropped connection still looking READY
      val caller = system.scheduler.scheduleWithFixedDelay(Duration.Zero, 200.millis)(() => call(channel))
      val failure =
        try channel.done.failed.futureValue
        finally caller.cancel()
      failure shouldBe a[ClientConnectionException]
      // the channel itself is shut down too, so calls fail fast with the give-up reason
      eventually {
        val callFailure = call(channel).failed.futureValue
        callFailure.getMessage should include("closed")
        callFailure.getCause shouldBe failure
      }
      failure.getMessage shouldBe "Unable to establish connection after [1] attempts"
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
