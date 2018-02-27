package io.grpc.testing.integration.test

import io.grpc.testing.integration2.ClientTester

import java.io.InputStream

import io.grpc.ManagedChannel
import io.grpc.testing.integration2.{ ChannelBuilder, Settings }

class AkkaGrpcClientTester(val settings: Settings) extends ClientTester {

  private var channel: ManagedChannel = null
  private var stub: TestServiceAkkaGrpc.TestServiceStub = null

  def createChannel(): ManagedChannel = ChannelBuilder.buildChannel(settings)

  def setUp(): Unit = {
    channel = createChannel()
    stub = TestServiceAkkaGrpc.stub(channel)
  }

  def tearDown(): Unit = {
    if (channel != null) channel.shutdown()
  }

  def emptyUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def cacheableUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
  }

  def largeUnary(): Unit = {
    throw new RuntimeException("Not implemented!")
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
