/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.InternalApi
import io.grpc.{ ConnectivityState, ManagedChannel }

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * INTERNAL API
 */
@InternalApi
object ChannelUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def close(internalChannel: InternalChannel)(implicit ec: ExecutionContext): Future[Done] = {
    internalChannel.managedChannel.foreach(_.shutdown())
    internalChannel.done
  }
  /**
   * INTERNAL API
   */
  @InternalApi
  def closeCS(internalChannel: InternalChannel)(implicit ec: ExecutionContext): CompletionStage[Done] = {
    close(internalChannel).toJava
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[akka] def monitorChannel(done: Promise[Done], channel: ManagedChannel, maxConnectionAttempts: Int): Unit = {

    def monitor(previousState: ConnectivityState, connectionAttempts: Int): Unit = {
      if (connectionAttempts == maxConnectionAttempts) {
        // shutdown is idempotent in ManagedChannelImpl
        channel.shutdown()
        done.tryFailure(new RuntimeException(s"Unable to establish connection after [$maxConnectionAttempts]"))
      } else {
        val currentState = channel.getState(false)
        if (currentState == ConnectivityState.SHUTDOWN) {
          done.trySuccess(Done)
        } else {
          channel.notifyWhenStateChanged(currentState, () => {
            if (currentState == ConnectivityState.TRANSIENT_FAILURE) {
              monitor(currentState, connectionAttempts + 1)
            } else if (currentState == ConnectivityState.READY) {
              monitor(currentState, 0)
            } else {
              // IDLE / CONNECTING / SHUTDOWN
              monitor(currentState, connectionAttempts)
            }
          })
        }
      }
    }

    monitor(channel.getState(false), 0)

  }
}
