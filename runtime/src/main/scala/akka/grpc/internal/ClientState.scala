/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.stream.{ ActorMaterializer, Materializer }
import io.grpc.ManagedChannel

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Failure

/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Client utilities taking care of Channel reconnection and Channel lifecycle in general.
 */
@InternalApi
class ClientState(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext) {

  private val internalChannelRef: AtomicReference[InternalChannel] = new AtomicReference[InternalChannel](create())
  private val closeDemand: Promise[Done] = Promise[Done]()

  mat match {
    case m: ActorMaterializer =>
      m.system.whenTerminated.foreach(_ => close())(ex)
    case _ =>
  }

  def withChannel[A](f: Future[ManagedChannel] => A): A = {
    val c = internalChannelRef.get()
    if (c != null)
      f(internalChannelRef.get().managedChannel)
    else // Likely to happen if this is a shared client during shutdown
      throw new ClientClosedException
  }

  def closed(): Future[Done] = {
    // while there's no request to close this RestartingClient, it will continue to restart.
    // Once there's demand, the `closed` future will redeem flatMapping with the `closed()`
    // future of the clientRef that's active at that moment.
    closeDemand.future.flatMap { _ =>
      val c = internalChannelRef.get()
      if (c != null)
        internalChannelRef.get().done
      else
        Future.successful(Done)
    }
  }

  @tailrec
  final def close(): Future[Done] = {
    closeDemand.trySuccess(Done)
    val c = internalChannelRef.get()
    if (c != null) {
      val done = ChannelUtils.close(c)
      if (internalChannelRef.compareAndSet(c, null.asInstanceOf[InternalChannel]))
        done
      else // client has had a ClientConnectionException and been re-created, need to shutdown the new one
        close()
    } else
      Future.successful(Done)
  }

  def channelFactory: InternalChannel = NettyClientUtils.createChannel(settings)

  private def create(): InternalChannel = {
    val c: InternalChannel = channelFactory
    c.done.onComplete {
      case Failure(_: ClientConnectionException) =>
        val old = internalChannelRef.get()
        if (old != null) {
          val newInternalClient = create()
          // Only one client is alive at a time. However a close() could have happened between the get() and this set
          if (!internalChannelRef.compareAndSet(old, newInternalClient)) {
            // close the newly created client we've been shutdown
            ChannelUtils.close(newInternalClient)
          }
        }
      case Failure(_) =>
        close()
      case _ =>
      // let all other exceptions and success through
    }
    c
  }
}
/**
 * INTERNAL API
 * Used from generated code so can't be private.
 *
 * Thrown if a withChannel call is called after closing the internal channel
 */
@InternalApi
class ClientClosedException() extends RuntimeException("withChannel called after close()")
