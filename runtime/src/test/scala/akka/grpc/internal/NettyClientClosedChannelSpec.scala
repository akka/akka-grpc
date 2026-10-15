/*
 * Copyright (C) 2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
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

  "A Netty client channel shut down after running out of connection attempts" should {

    "fail new streaming calls with UNAVAILABLE" in {
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

      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode shouldBe Status.Code.UNAVAILABLE
    }

    "fail new unary calls with UNAVAILABLE" in {
      val channel = channelClosedAfterConnectionAttempts()

      val failure = channel
        .invoke(
          Array.emptyByteArray,
          MetadataImpl.empty,
          descriptor(MethodDescriptor.MethodType.UNARY),
          CallOptions.DEFAULT)
        .failed
        .futureValue

      failure shouldBe a[StatusRuntimeException]
      failure.asInstanceOf[StatusRuntimeException].getStatus.getCode shouldBe Status.Code.UNAVAILABLE
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

      failure shouldBe a[StatusRuntimeException]
      val status = failure.asInstanceOf[StatusRuntimeException].getStatus
      status.getCode shouldBe Status.Code.UNAVAILABLE
      status.getCause shouldBe a[ClientConnectionException]
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
