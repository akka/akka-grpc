/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.OptionVal
import io.grpc.ManagedChannel

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

/**
  * INTERNAL API
  * Used from generated code so can't be private.
  *
  * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
  */
@InternalApi
final class ClientState(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext) {

  private val internalChannelRef: AtomicReference[OptionVal[InternalChannel]] =
    new AtomicReference[OptionVal[InternalChannel]](OptionVal(create()))
  // usually null, it'll have a value when the underlying InternalChannel is closing or closed.
  private val closing: AtomicReference[Future[Done]] = new AtomicReference[Future[Done]](null)
  private val closeDemand: Promise[Done] = Promise[Done]()

  mat match {
    case m: ActorMaterializer =>
      m.system.whenTerminated.foreach(_ => close())(ex)
    case _ =>
  }

  def withChannel[A](f: Future[ManagedChannel] => A): A =
    f {
      internalChannelRef
        .get()
        .getOrElse(throw new ClientClosedException)
        .managedChannel
    }

  def closed(): Future[Done] = {
    // while there's no request to close this RestartingClient, it will continue to restart.
    // Once there's demand, the `closeDemand` future will redeem flatMapping with the `closing`
    // future which is a reference to promise of the internalChannel close status.
    closeDemand.future.flatMap { _ =>
      // `closeDemand` guards the read access to `closing`
      closing.get()
    }
  }

  @tailrec
  def close(): Future[Done] = {
    val maybeChannel = internalChannelRef.get()
    if (maybeChannel.isDefined) {
      val channel = maybeChannel.get
      // invoke `close` on the channel and capture the `channel.done` returned
      val done = ChannelUtils.close(channel)
      // set the `closing` to the current `channel.done`
      closing.compareAndSet(null, done)
      // notify there's been close demand (see `def closed()` above)
      closeDemand.trySuccess(Done)

      if (internalChannelRef.compareAndSet(maybeChannel, OptionVal.None)) {
        closing.get()
      } else {
        // when internalChannelRef was not maybeChannel
        if (internalChannelRef.get.isDefined) {
          // client has had a ClientConnectionException and been re-created, need to shutdown the new one
          close()
        } else {
          // or a competing thread already set `internalChannelRef` to None and CAS failed.
          closing.get()
        }
      }
    } else {
      closing.compareAndSet(null, Future.successful(Done))
      closeDemand.trySuccess(Done)
      closing.get()
    }
  }

  // not private to overwrite it on unit tests
  def channelFactory: InternalChannel = NettyClientUtils.createChannel(settings)

  private def create(): InternalChannel = {
    val internalChannel: InternalChannel = channelFactory
    internalChannel.done.onComplete {
      case Failure(_: ClientConnectionException) =>
        val old = internalChannelRef.get()
        if (old.isDefined) {
          val newInternalChannel = create()
          // Only one client is alive at a time. However a close() could have happened between the get() and this set
          if (!internalChannelRef.compareAndSet(old, OptionVal(newInternalChannel))) {
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
