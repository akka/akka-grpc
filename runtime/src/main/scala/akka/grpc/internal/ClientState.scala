/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import io.grpc.ManagedChannel

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Failure
import scala.compat.java8.FutureConverters._

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
 */
@InternalApi
final class ClientState(settings: GrpcClientSettings, channelFactory: GrpcClientSettings => InternalChannel)(
    implicit mat: Materializer,
    ex: ExecutionContext) {

  def this(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext) =
    this(settings, s => NettyClientUtils.createChannel(s))

  private val internalChannelRef =
    new AtomicReference[Option[InternalChannel]](Some(create()))
  // usually None, it'll have a value when the underlying InternalChannel is closing or closed.
  private val closing = new AtomicReference[Option[Future[Done]]](None)
  private val closeDemand: Promise[Done] = Promise[Done]()

  mat.system.whenTerminated.foreach(_ => close())(ex)

  def withChannel[A](f: Future[ManagedChannel] => A): A =
    f {
      internalChannelRef.get().getOrElse(throw new ClientClosedException).managedChannel
    }

  def closedCS(): CompletionStage[Done] = closed().toJava
  def closeCS(): CompletionStage[Done] = close().toJava

  def closed(): Future[Done] =
    // while there's no request to close this RestartingClient, it will continue to restart.
    // Once there's demand, the `closeDemand` future will redeem flatMapping with the `closing`
    // future which is a reference to promise of the internalChannel close status.
    closeDemand.future.flatMap { _ =>
      // `closeDemand` guards the read access to `closing`
      closing.get().get
    }

  @tailrec
  def close(): Future[Done] = {
    val maybeChannel = internalChannelRef.get()
    maybeChannel match {
      case Some(channel) =>
        // invoke `close` on the channel and capture the `channel.done` returned
        val done = ChannelUtils.close(channel)
        // set the `closing` to the current `channel.done`
        closing.compareAndSet(None, Some(done))
        // notify there's been close demand (see `def closed()` above)
        closeDemand.trySuccess(Done)

        if (internalChannelRef.compareAndSet(maybeChannel, None)) {
          done
        } else {
          // when internalChannelRef was not maybeChannel
          if (internalChannelRef.get.isDefined) {
            // client has had a ClientConnectionException and been re-created, need to shutdown the new one
            close()
          } else {
            // or a competing thread already set `internalChannelRef` to None and CAS failed.
            done
          }
        }
      case _ =>
        // set the `closing` to immediate success
        val done = Future.successful(Done)
        closing.compareAndSet(None, Some(done))
        // notify there's been close demand (see `def closed()` above)
        closeDemand.trySuccess(Done)
        done
    }
  }

  private def create(): InternalChannel = {
    val internalChannel: InternalChannel = channelFactory(settings)
    internalChannel.done.onComplete {
      case Failure(_: ClientConnectionException | _: NoTargetException) =>
        val old = internalChannelRef.get()
        if (old.isDefined) {
          val newInternalChannel = create()
          // Only one client is alive at a time. However a close() could have happened between the get() and this set
          if (!internalChannelRef.compareAndSet(old, Some(newInternalChannel))) {
            // close the newly created client we've been shutdown
            ChannelUtils.close(newInternalChannel)
          }
        }
      case Failure(_) =>
        close()
      case _ =>
      // let success through
    }
    internalChannel
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
