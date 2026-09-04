/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings

import io.grpc.{ ConnectivityState, ManagedChannel }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ Future, Promise }

/**
 * Used to indicate that a gRPC client can not establish a connection
 * after the configured number of attempts.
 *
 * Can be caught to re-create the client if it is likely that
 * your service discovery mechanism will resolve to different instances.
 */
class ClientConnectionException(msg: String) extends RuntimeException(msg)

/**
 * INTERNAL API
 */
@InternalApi
object ChannelUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[akka] def create(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    settings.backend match {
      case "netty" =>
        NettyClientUtils.createChannel(settings, log)(sys.classicSystem.dispatcher, sys.classicSystem)
      case "akka-http" =>
        AkkaHttpClientUtils.createChannel(settings, log)
      case _ => throw new IllegalArgumentException(s"Unexpected backend [${settings.backend}]")
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  def close(internalChannel: InternalChannel): Future[Done] = {
    internalChannel.shutdown()
    internalChannel.done
  }

  /**
   * INTERNAL API
   *
   * @param scheduleOnce used to run the connecting-timeout watchdog (see `connectingTimeout`); takes a delay
   *                     and the task to run after that delay
   */
  @InternalApi
  private[akka] def monitorChannel(
      ready: Promise[Unit],
      done: Promise[Done],
      channel: ManagedChannel,
      maxConnectionAttempts: Option[Int],
      connectingTimeout: Duration,
      log: LoggingAdapter)(scheduleOnce: (FiniteDuration, () => Unit) => Unit): Unit = {

    // grpc-java has no timeout for the CONNECTING state as a whole (see grpc/grpc-java#1943):
    // if the TCP connection is established but the peer never completes the TLS/HTTP2 handshake,
    // the channel can stay CONNECTING forever, and since it never reaches TRANSIENT_FAILURE the
    // connectionAttempts counter below never advances either. Abandon such stuck attempts after
    // `connectingTimeout` by forcing the channel back to IDLE (tearing down the wedged
    // subchannel) and immediately requesting a fresh connection.
    def watchForStuckConnecting(): Unit = connectingTimeout match {
      case timeout: FiniteDuration =>
        scheduleOnce(
          timeout,
          () =>
            if (channel.getState(false) == ConnectivityState.CONNECTING) {
              log.warning(
                "gRPC client channel has been CONNECTING for more than {}, abandoning the stuck connection " +
                "attempt and starting a new one (see akka.grpc.client config setting 'connecting-timeout')",
                timeout)
              channel.enterIdle()
              channel.getState(true)
            })
      case _ => // connecting-timeout is 'infinite': watchdog disabled
    }

    def monitor(currentState: ConnectivityState, connectionAttempts: Int): Unit = {
      log.debug(s"monitoring with state $currentState and connectionAttempts $connectionAttempts")
      val newAttemptOpt = currentState match {
        case ConnectivityState.TRANSIENT_FAILURE =>
          if (maxConnectionAttempts.contains(connectionAttempts + 1)) {
            val ex = new ClientConnectionException(s"Unable to establish connection after [$maxConnectionAttempts]")
            ready.tryFailure(ex) || done.tryFailure(ex)
            None
          } else Some(connectionAttempts + 1)

        case ConnectivityState.READY =>
          ready.trySuccess(())
          Some(0)

        case ConnectivityState.SHUTDOWN =>
          done.trySuccess(Done)
          None

        case ConnectivityState.CONNECTING =>
          watchForStuckConnecting()
          Some(connectionAttempts)

        case ConnectivityState.IDLE =>
          Some(connectionAttempts)
      }
      newAttemptOpt.foreach { attempts =>
        channel.notifyWhenStateChanged(currentState, () => monitor(channel.getState(false), attempts))
      }
    }
    monitor(channel.getState(false), 0)
  }

}
