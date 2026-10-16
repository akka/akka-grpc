/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcResponseMetadata
import akka.grpc.GrpcSingleResponse
import akka.pattern.RetrySettings
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.Status
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object RequestBuilderImplSpec {
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

  /** Fails every call with UNAVAILABLE, and counts them */
  final class FailingChannel extends InternalChannel {
    val calls = new AtomicInteger(0)
    val closed = Promise[Done]()

    private def failure = Status.UNAVAILABLE.withDescription("unavailable").asRuntimeException()

    override def invoke[I, O](
        request: I,
        headers: MetadataImpl,
        descriptor: MethodDescriptor[I, O],
        options: CallOptions): Future[O] = {
      calls.incrementAndGet()
      Future.failed(failure)
    }

    override def invokeWithMetadata[I, O](
        request: I,
        headers: MetadataImpl,
        descriptor: MethodDescriptor[I, O],
        options: CallOptions): Future[GrpcSingleResponse[O]] = {
      calls.incrementAndGet()
      Future.failed(failure)
    }

    override def invokeWithMetadata[I, O](
        source: Source[I, NotUsed],
        headers: MetadataImpl,
        descriptor: MethodDescriptor[I, O],
        streamingResponse: Boolean,
        options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = {
      calls.incrementAndGet()
      val f = failure
      Source.failed[O](f).mapMaterializedValue(_ => Future.failed[GrpcResponseMetadata](f))
    }

    override def shutdown(): Unit = closed.trySuccess(Done)
    override def done: Future[Done] = closed.future
  }
}

class RequestBuilderImplSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import RequestBuilderImplSpec._

  implicit val system: ActorSystem = ActorSystem("RequestBuilderImplSpec")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = Materializer(system)

  private val settings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false)
  private val retrySettings = RetrySettings(3).withFixedDelay(10.millis)

  private def unary(channel: InternalChannel) =
    new ScalaUnaryRequestBuilder(
      descriptor(MethodDescriptor.MethodType.UNARY),
      channel,
      CallOptions.DEFAULT,
      settings,
      MetadataImpl.empty,
      None).withRetry(retrySettings)

  private def clientStreaming(channel: InternalChannel) =
    new ScalaClientStreamingRequestBuilder(
      descriptor(MethodDescriptor.MethodType.CLIENT_STREAMING),
      channel,
      CallOptions.DEFAULT,
      settings,
      MetadataImpl.empty,
      None).withRetry(retrySettings)

  "A request with retry settings" should {

    "retry failed calls while the client is running" in {
      val channel = new FailingChannel
      unary(channel).invoke(Array.emptyByteArray).failed.futureValue
      channel.calls.get shouldBe 4
    }

    "not retry unary calls once the client is closed" in {
      val channel = new FailingChannel
      channel.shutdown()
      unary(channel).invoke(Array.emptyByteArray).failed.futureValue
      channel.calls.get shouldBe 1
    }

    "not retry unary calls with metadata once the client is closed" in {
      val channel = new FailingChannel
      channel.shutdown()
      unary(channel).invokeWithMetadata(Array.emptyByteArray).failed.futureValue
      channel.calls.get shouldBe 1
    }

    "not retry client streaming calls once the client is closed" in {
      val channel = new FailingChannel
      channel.shutdown()
      clientStreaming(channel).invoke(Source.single(Array.emptyByteArray)).failed.futureValue
      channel.calls.get shouldBe 1
    }

    "not retry once the client has given up connecting" in {
      val channel = new FailingChannel
      channel.closed.tryFailure(new ClientConnectionException("gave up"))
      unary(channel).invoke(Array.emptyByteArray).failed.futureValue
      channel.calls.get shouldBe 1
    }

    "still apply the user decider while the client is running" in {
      val channel = new FailingChannel
      val builder = new ScalaUnaryRequestBuilder(
        descriptor(MethodDescriptor.MethodType.UNARY),
        channel,
        CallOptions.DEFAULT,
        settings,
        MetadataImpl.empty,
        None).withRetry(retrySettings.withDecider(_ => false))
      builder.invoke(Array.emptyByteArray).failed.futureValue
      channel.calls.get shouldBe 1
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
