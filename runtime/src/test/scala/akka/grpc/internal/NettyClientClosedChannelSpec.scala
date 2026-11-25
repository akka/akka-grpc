/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.discovery.Lookup
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import io.grpc.CallOptions
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

object NettyClientClosedChannelSpec {
  object BytesMarshaller extends MethodDescriptor.Marshaller[Array[Byte]] {
    override def stream(value: Array[Byte]): InputStream = new ByteArrayInputStream(value)
    override def parse(stream: InputStream): Array[Byte] = stream.readAllBytes()
  }

  def descriptor(methodType: MethodDescriptor.MethodType): MethodDescriptor[Array[Byte], Array[Byte]] =
    MethodDescriptor
      .newBuilder(BytesMarshaller, BytesMarshaller)
      .setType(methodType)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("test.Service", "Method"))
      .build()

  final class NeverAnsweringDiscovery extends ServiceDiscovery {
    override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = Promise[Resolved]().future
  }

  val trailerKey: Metadata.Key[String] = Metadata.Key.of("test-trailer", Metadata.ASCII_STRING_MARSHALLER)

  // hands each started call to the test, which decides how the call ends
  def startServer(
      methodDescriptor: MethodDescriptor[Array[Byte], Array[Byte]],
      started: Promise[ServerCall[Array[Byte], Array[Byte]]]): Server = {
    val handler = new ServerCallHandler[Array[Byte], Array[Byte]] {
      override def startCall(
          call: ServerCall[Array[Byte], Array[Byte]],
          headers: Metadata): ServerCall.Listener[Array[Byte]] = {
        started.success(call)
        new ServerCall.Listener[Array[Byte]] {}
      }
    }
    NettyServerBuilder
      .forPort(0)
      .addService(ServerServiceDefinition.builder("test.Service").addMethod(methodDescriptor, handler).build())
      .build()
      .start()
  }

  def unusedPort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }
}

/**
 * Reproduces a client whose channel akka-grpc shut down itself after running out of connection attempts,
 * once grpc-java has also shut down the channel executor (the default shared executor is shut down when the
 * last channel using it terminates). New calls should fail with a meaningful status, not UNKNOWN caused by a
 * RejectedExecutionException in the SynchronizationContext.
 */
class NettyClientClosedChannelSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import NettyClientClosedChannelSpec._

  implicit val system: ActorSystem = ActorSystem("NettyClientClosedChannelSpec")
  implicit val ec: ExecutionContext = system.dispatcher

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, Span(50, org.scalatest.time.Millis))

  private def channelClosedAfterConnectionAttempts(): InternalChannel = {
    // stands in for grpc-java's shared channel executor, so that we control when it is terminated
    val executor = new ThreadPoolExecutor(0, 4, 1, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]())
    val settings = GrpcClientSettings
      .connectToServiceAt("127.0.0.1", unusedPort())
      .withTls(false)
      .withConnectionAttempts(1)
      .withChannelBuilderOverrides(_.executor(executor))
    val channel = NettyClientUtils.createChannel(settings, system.log)

    // first call triggers connecting, the attempt fails and akka-grpc gives up and shuts down the channel
    channel
      .invoke(
        Array.emptyByteArray,
        MetadataImpl.empty,
        descriptor(MethodDescriptor.MethodType.UNARY),
        CallOptions.DEFAULT)
      .failed
      .futureValue
    channel.done.failed.futureValue shouldBe a[ClientConnectionException]

    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS) shouldBe true
    channel
  }

  // UNAVAILABLE alone is also what grpc-java reports while its executor is still alive
  private def assertClosedFailure(failure: Throwable): Unit = {
    failure shouldBe a[StatusRuntimeException]
    val status = failure.asInstanceOf[StatusRuntimeException].getStatus
    status.getCode shouldBe Status.Code.UNAVAILABLE
    status.getDescription should include("closed")
    status.getCause shouldBe a[ClientConnectionException]
  }

  "A Netty client channel shut down after running out of connection attempts" should {

    "fail new streaming calls with UNAVAILABLE caused by the connection failure" in {
      val channel = channelClosedAfterConnectionAttempts()

      val failure = channel
        .invokeWithMetadata(
          Source.single(Array.emptyByteArray),
          MetadataImpl.empty,
          descriptor(MethodDescriptor.MethodType.SERVER_STREAMING),
          streamingResponse = true,
          CallOptions.DEFAULT)
        .runWith(Sink.ignore)
        .failed
        .futureValue

      assertClosedFailure(failure)
    }

    "fail new unary calls with UNAVAILABLE caused by the connection failure" in {
      val channel = channelClosedAfterConnectionAttempts()

      val failure = channel
        .invoke(
          Array.emptyByteArray,
          MetadataImpl.empty,
          descriptor(MethodDescriptor.MethodType.UNARY),
          CallOptions.DEFAULT)
        .failed
        .futureValue

      assertClosedFailure(failure)
    }

    "fail new unary calls with metadata with UNAVAILABLE caused by the connection failure" in {
      val channel = channelClosedAfterConnectionAttempts()

      val failure = channel
        .invokeWithMetadata(
          Array.emptyByteArray,
          MetadataImpl.empty,
          descriptor(MethodDescriptor.MethodType.UNARY),
          CallOptions.DEFAULT)
        .failed
        .futureValue

      assertClosedFailure(failure)
    }
  }

  // calls waiting for name resolution are cancelled by grpc-java's shutdownNow, rather than failed
  private def channelGivingUpWhileResolving(): InternalChannel = {
    val settings = GrpcClientSettings
      .usingServiceDiscovery("greeter", new NeverAnsweringDiscovery)
      .withTls(false)
      .withConnectionAttempts(1)
      .withConnectingTimeout(200.millis)
    NettyClientUtils.createChannel(settings, system.log)
  }

  private def assertGaveUpFailure(failure: Throwable): Unit = {
    assertClosedFailure(failure)
    failure.asInstanceOf[StatusRuntimeException].getTrailers should not be null
  }

  "A Netty client channel giving up while name resolution is pending" should {

    "fail waiting unary calls with UNAVAILABLE caused by the connection failure" in {
      val channel = channelGivingUpWhileResolving()
      val waiting = channel.invoke(
        Array.emptyByteArray,
        MetadataImpl.empty,
        descriptor(MethodDescriptor.MethodType.UNARY),
        CallOptions.DEFAULT)
      channel.done.failed.futureValue shouldBe a[ClientConnectionException]

      assertGaveUpFailure(waiting.failed.futureValue)
    }

    "fail waiting unary calls with metadata with UNAVAILABLE caused by the connection failure" in {
      val channel = channelGivingUpWhileResolving()
      val waiting = channel.invokeWithMetadata(
        Array.emptyByteArray,
        MetadataImpl.empty,
        descriptor(MethodDescriptor.MethodType.UNARY),
        CallOptions.DEFAULT)
      channel.done.failed.futureValue shouldBe a[ClientConnectionException]

      assertGaveUpFailure(waiting.failed.futureValue)
    }

    "fail waiting streaming calls with UNAVAILABLE caused by the connection failure" in {
      val channel = channelGivingUpWhileResolving()
      val waiting = channel
        .invokeWithMetadata(
          Source.single(Array.emptyByteArray),
          MetadataImpl.empty,
          descriptor(MethodDescriptor.MethodType.SERVER_STREAMING),
          streamingResponse = true,
          CallOptions.DEFAULT)
        .runWith(Sink.ignore)
      channel.done.failed.futureValue shouldBe a[ClientConnectionException]

      assertGaveUpFailure(waiting.failed.futureValue)
    }
  }

  // an orderly shutdown lets in-flight calls finish, their status must reach the caller unchanged
  private def cancelledByServerAfterShutdown(methodType: MethodDescriptor.MethodType)(
      startCall: (InternalChannel, MethodDescriptor[Array[Byte], Array[Byte]]) => Future[Any]): Unit = {
    val methodDescriptor = descriptor(methodType)
    val started = Promise[ServerCall[Array[Byte], Array[Byte]]]()
    val server = startServer(methodDescriptor, started)
    try {
      val settings = GrpcClientSettings.connectToServiceAt("127.0.0.1", server.getPort).withTls(false)
      val channel = NettyClientUtils.createChannel(settings, system.log)
      val inFlight = startCall(channel, methodDescriptor)
      val serverCall = started.future.futureValue

      channel.shutdown()
      val trailers = new Metadata()
      trailers.put(trailerKey, "from-server")
      serverCall.close(Status.CANCELLED.withDescription("cancelled by server"), trailers)

      val failure = inFlight.failed.futureValue
      failure shouldBe a[StatusRuntimeException]
      val exception = failure.asInstanceOf[StatusRuntimeException]
      exception.getStatus.getCode shouldBe Status.Code.CANCELLED
      exception.getStatus.getDescription shouldBe "cancelled by server"
      exception.getTrailers.get(trailerKey) shouldBe "from-server"
      channel.done.futureValue
    } finally server.shutdownNow()
  }

  "A Netty client channel closed with an orderly shutdown" should {

    "keep the CANCELLED status of in-flight unary calls" in {
      cancelledByServerAfterShutdown(MethodDescriptor.MethodType.UNARY) { (channel, methodDescriptor) =>
        channel.invoke(Array.emptyByteArray, MetadataImpl.empty, methodDescriptor, CallOptions.DEFAULT)
      }
    }

    "keep the CANCELLED status of in-flight unary calls with metadata" in {
      cancelledByServerAfterShutdown(MethodDescriptor.MethodType.UNARY) { (channel, methodDescriptor) =>
        channel.invokeWithMetadata(Array.emptyByteArray, MetadataImpl.empty, methodDescriptor, CallOptions.DEFAULT)
      }
    }

    "keep the CANCELLED status of in-flight streaming calls" in {
      cancelledByServerAfterShutdown(MethodDescriptor.MethodType.SERVER_STREAMING) { (channel, methodDescriptor) =>
        channel
          .invokeWithMetadata(
            Source.single(Array.emptyByteArray),
            MetadataImpl.empty,
            methodDescriptor,
            streamingResponse = true,
            CallOptions.DEFAULT)
          .runWith(Sink.ignore)
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
