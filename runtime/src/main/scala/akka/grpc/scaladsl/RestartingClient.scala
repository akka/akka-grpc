/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.annotation.ApiMayChange
import akka.grpc.internal.ClientConnectionException
import akka.grpc.scaladsl.RestartingClient.ClientClosedException
import play.libs.F.PromiseTimeoutException

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Failure

object RestartingClient {
  def apply[T <: AkkaGrpcClient](createClient: () => T)(implicit ec: ExecutionContext): RestartingClient[T] =
    new RestartingClient[T](createClient)

  /**
   * Thrown if a withClient call is called after
   */
  class ClientClosedException() extends RuntimeException("withClient called after close()")

}

/**
 * Wraps a Akka gRPC  client and restarts it if a [ClientConnectionException] is thrown.
 * All other exceptions result in closing and any calls to withClient throwing
 * a [ClientClosedException].
 */

@ApiMayChange
final class RestartingClient[T <: AkkaGrpcClient](createClient: () => T)(implicit ec: ExecutionContext) extends AkkaGrpcClient {

  private val clientRef = new AtomicReference[T](create())
  private val closeDemand: Promise[Done] = Promise[Done]()

  def withClient[A](f: T => A): A = {
    val c = clientRef.get()
    if (c != null)
      f(clientRef.get())
    else // Likely to happen if this is a shared client during shutdown
      throw new ClientClosedException
  }

  @tailrec
  override def close(): Future[Done] = {
    closeDemand.trySuccess(Done)
    val c = clientRef.get()
    if (c != null) {
      val done = c.close()
      if (clientRef.compareAndSet(c, null.asInstanceOf[T]))
        done
      else // client has had a ClientConnectionException and been re-created, need to shutdown the new one
        close()
    } else
      Future.successful(Done)
  }

  override def closed(): Future[Done] = {
    // while there's no request to close this RestartingClient, it will continue to restart.
    // Once there's demand, the `closed` future will redeem flatMapping with the `closed()`
    // future of the clientRef that's active at that moment.
    closeDemand.future.flatMap { _ =>
      val c = clientRef.get()
      if (c != null)
        c.closed()
      else
        Future.successful(Done)
    }
  }

  private def create(): T = {
    val c: T = createClient()
    c.closed().onComplete {
      case Failure(_: ClientConnectionException) =>
        val old = clientRef.get()
        if (old != null) {
          val newClient = create()
          // Only one client is alive at a time. However a close() could have happened between the get() and this set
          if (!clientRef.compareAndSet(old, newClient)) {
            // close the newly created client we've been shutdown
            newClient.close()
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

