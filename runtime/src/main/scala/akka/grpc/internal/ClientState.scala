/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.pattern.Patterns
import akka.stream.{ ActorMaterializer, Materializer }
import io.grpc.ManagedChannel

import scala.annotation.tailrec
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Failure

/**
 * INTERNAL API
 *
 * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
 */
@InternalApi
final class ClientState(settings: GrpcClientSettings, log: LoggingAdapter, channel: InternalChannel)(
    implicit mat: Materializer,
    ex: ExecutionContext) {

  @InternalStableApi
  def this(settings: GrpcClientSettings, log: LoggingAdapter)(implicit mat: Materializer, ex: ExecutionContext) =
    this(settings, log, NettyClientUtils.createChannel(settings, log))

  mat match {
    case m: ActorMaterializer =>
      m.system.whenTerminated.foreach(_ => close())(ex)
    case _ =>
  }

  // TODO #761 consider not leaking ManagedChannel to the generated code?
  @InternalStableApi
  def withChannel[A](f: Future[ManagedChannel] => A): A =
    f { Future.successful(channel.managedChannel) }

  def closedCS(): CompletionStage[Done] = closed().toJava
  def closeCS(): CompletionStage[Done] = close().toJava

  def closed(): Future[Done] = channel.done

  def close(): Future[Done] = ChannelUtils.close(channel)
}

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Thrown if a withChannel call is called after closing the internal channel
 */
@InternalApi
final class ClientClosedException() extends RuntimeException("withChannel called after close()")
