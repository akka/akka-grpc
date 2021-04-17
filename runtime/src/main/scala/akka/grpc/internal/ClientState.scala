/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.Grpc

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

/**
 * INTERNAL API
 *
 * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
 */
@InternalApi
final class ClientState(@InternalStableApi val internalChannel: InternalChannel)(
    implicit sys: ClassicActorSystemProvider) {

  @InternalStableApi
  def this(settings: GrpcClientSettings, log: LoggingAdapter)(implicit sys: ClassicActorSystemProvider) =
    this(ClientState.createPool(settings, log))

  Grpc(sys).registerClient(this)

  def closedCS(): CompletionStage[Done] = closed().toJava
  def closeCS(): CompletionStage[Done] = close().toJava

  def closed(): Future[Done] = internalChannel.done

  def close(): Future[Done] = {
    Grpc(sys).deregisterClient(this)
    ChannelUtils.close(internalChannel)
  }
}

object ClientState {
  def createPool(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    settings.backend match {
      case "netty" =>
        NettyClientUtils.createChannel(settings, log)(sys.classicSystem.dispatcher)
      case "akka-http" =>
        AkkaHttpClientUtils.createChannel(settings, log)
      case _ => throw new IllegalArgumentException(s"Unexpected backend [${settings.backend}]")
    }
  }
}

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Thrown if a withChannel call is called after closing the internal channel
 */
@InternalApi
final class ClientClosedException() extends RuntimeException("withChannel called after close()")
