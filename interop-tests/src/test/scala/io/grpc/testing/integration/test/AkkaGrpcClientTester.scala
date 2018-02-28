package io.grpc.testing.integration.test

import java.io.InputStream

import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import io.grpc.ManagedChannel
import io.grpc.testing.integration.messages.{ Payload, PayloadType, SimpleRequest, SimpleResponse }
import io.grpc.testing.integration2.{ ChannelBuilder, ClientTester, Settings }
import org.junit.Assert.assertEquals

import scala.concurrent.Await
import scala.concurrent.duration._
class AkkaGrpcClientTester(val settings: Settings) extends ClientTester {

  private var channel: ManagedChannel = null
  private var stub: TestServiceAkkaGrpc.TestServiceStub = null

  private val awaitTimeout = 3.seconds
  def createChannel(): ManagedChannel = ChannelBuilder.buildChannel(settings)

  def setUp(): Unit = {
    channel = createChannel()
    stub = TestServiceAkkaGrpc.stub(channel)
  }

  def tearDown(): Unit = {
    if (channel != null) channel.shutdown()
  }

  def emptyUnary(): Unit = {
    assertEquals(Empty(), Await.result(stub.emptyCall(Empty()), awaitTimeout))
  }

  def cacheableUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def largeUnary(): Unit = {
    val request =
      SimpleRequest(
        PayloadType.COMPRESSABLE,
        responseSize = 314159,
        payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](271828)))))

    val goldenResponse = SimpleResponse(payload = Option(Payload(body = ByteString.copyFrom(new Array[Byte](314159)))))

    val response = Await.result(stub.unaryCall(request), awaitTimeout)
    assertEquals(goldenResponse, response)
  }

  def clientCompressedUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serverCompressedUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def clientStreaming(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def clientCompressedStreaming(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serverStreaming(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serverCompressedStreaming(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def pingPong(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def emptyStream(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def computeEngineCreds(serviceAccount: String, oauthScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def serviceAccountCreds(jsonKey: String, credentialsStream: InputStream, authScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def jwtTokenCreds(serviceAccountJson: InputStream): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def oauth2AuthToken(jsonKey: String, credentialsStream: InputStream, authScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def perRpcCreds(jsonKey: String, credentialsStream: InputStream, oauthScope: String): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def customMetadata(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def statusCodeAndMessage(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def unimplementedMethod(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def unimplementedService(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def cancelAfterBegin(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def cancelAfterFirstResponse(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def timeoutOnSleepingServer(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

}
