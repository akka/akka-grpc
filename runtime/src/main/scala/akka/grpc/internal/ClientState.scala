/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.stream.{ ActorMaterializer, Materializer }

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 *
 * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
 */
@InternalApi
final class ClientState(@InternalStableApi val internalChannel: InternalChannel)(
    implicit mat: Materializer,
    ex: ExecutionContext) {

  @InternalStableApi
  def this(settings: GrpcClientSettings, log: LoggingAdapter)(implicit mat: Materializer, ex: ExecutionContext) =
    this(NettyClientUtils.createChannel(settings, log))

  mat match {
    case m: ActorMaterializer =>
      m.system.whenTerminated.foreach(_ => close())(ex)
    case _ =>
  }

  def closedCS(): CompletionStage[Done] = closed().toJava
  def closeCS(): CompletionStage[Done] = close().toJava

  def closed(): Future[Done] = internalChannel.done

  def close(): Future[Done] = ChannelUtils.close(internalChannel)
}

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Thrown if a withChannel call is called after closing the internal channel
 */
@InternalApi
final class ClientClosedException() extends RuntimeException("withChannel called after close()")
