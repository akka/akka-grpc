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

  def createDonePromise(): Promise[Done] = Promise[Done]()

  def close(channel: Future[ManagedChannel], done: Promise[Done])(implicit ec: ExecutionContext): Future[Done] = {
    channel.foreach(_.shutdown())
    done.future
  }

  def closeCS(channel: Future[ManagedChannel], done: Promise[Done])(implicit ec: ExecutionContext): CompletionStage[Done] = {
    close(channel, done).toJava
  }

  def monitorChannel(done: Promise[Done], channel: ManagedChannel, maxConnectionAttempts: Int): Unit = {

    def monitor(previousState: ConnectivityState, connectionAttempts: Int): Unit = {
      if (connectionAttempts == maxConnectionAttempts) {
        // shutdown is idempotent in ManagedChannelImpl
        channel.shutdown()
        done.failure(new RuntimeException("Unable to establish connection"))
      } else {
        val currentState = channel.getState(false)
        if (currentState == ConnectivityState.SHUTDOWN) {
          done.success(Done)
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
