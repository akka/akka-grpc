/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done

import akka.actor.Cancellable
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings

import io.grpc.{ ConnectivityState, ManagedChannel }

import java.util.concurrent.atomic.AtomicBoolean
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
      log: LoggingAdapter)(scheduleOnce: (FiniteDuration, () => Unit) => Cancellable): Unit = {

    // A connection attempt that never counts as a failure keeps the client retrying forever,
    // even if the user configured maxConnectionAttempts to fail fast - so a watchdog-abandoned
    // attempt is counted exactly like a TRANSIENT_FAILURE.
    def failOrKeepGoing(nextAttempts: Int): Option[Int] =
      if (maxConnectionAttempts.contains(nextAttempts)) {
        val ex = new ClientConnectionException(s"Unable to establish connection after [$maxConnectionAttempts]")
        ready.tryFailure(ex) || done.tryFailure(ex)
        None
      } else Some(nextAttempts)

    def monitor(currentState: ConnectivityState, connectionAttempts: Int): Unit = {
      log.debug(s"monitoring with state $currentState and connectionAttempts $connectionAttempts")

      // Both the watchdog below and the "normal" state-change callback registered further down
      // can end up deciding what happens next for this one CONNECTING episode - whichever fires
      // first should win, and the other must become a no-op (guarded by this flag). Without it, a
      // watchdog-driven continuation and the notifyWhenStateChanged-driven one could both fire for
      // the same episode, double-counting the attempt or acting on stale data.
      val advanced = new AtomicBoolean(false)

      // grpc-java has no timeout for the CONNECTING state as a whole (see grpc/grpc-java#1943):
      // if the TCP connection is established but the peer never completes the TLS/HTTP2 handshake,
      // the channel can stay CONNECTING forever, and since it never reaches TRANSIENT_FAILURE it
      // would otherwise never count as a failed attempt either. Abandon such stuck attempts after
      // `connectingTimeout` by forcing the channel back to IDLE (tearing down the wedged
      // subchannel) and immediately requesting a fresh connection - counted as a failed attempt.
      //
      // The returned Cancellable must be cancelled as soon as this particular CONNECTING episode
      // ends (see below) - otherwise a stale timer from an earlier episode could fire during a
      // later, legitimate CONNECTING episode (e.g. after a normal idle-and-reconnect cycle) and
      // abandon it prematurely, even though that one hasn't been stuck at all.
      val pendingWatchdog: Option[Cancellable] =
        if (currentState != ConnectivityState.CONNECTING) None
        else
          connectingTimeout match {
            case timeout: FiniteDuration =>
              Some(
                scheduleOnce(
                  timeout,
                  () =>
                    // Check state before claiming `advanced`: if we're not actually stuck, we must
                    // NOT prevent the real notifyWhenStateChanged callback from doing its job below.
                    if (channel.getState(false) == ConnectivityState.CONNECTING && advanced
                        .compareAndSet(false, true)) {
                      log.warning(
                        "gRPC client channel has been CONNECTING for more than {}, abandoning the stuck " +
                        "connection attempt and starting a new one (see akka.grpc.client config setting " +
                        "'connecting-timeout')",
                        timeout)
                      failOrKeepGoing(connectionAttempts + 1).foreach { attempts =>
                        channel.enterIdle()
                        monitor(channel.getState(true), attempts)
                      }
                    }))
            case _ => None // connecting-timeout is 'infinite': watchdog disabled
          }

      val newAttemptOpt = currentState match {
        case ConnectivityState.TRANSIENT_FAILURE =>
          failOrKeepGoing(connectionAttempts + 1)

        case ConnectivityState.READY =>
          ready.trySuccess(())
          Some(0)

        case ConnectivityState.SHUTDOWN =>
          done.trySuccess(Done)
          None

        case ConnectivityState.CONNECTING =>
          Some(connectionAttempts)

        case ConnectivityState.IDLE =>
          Some(connectionAttempts)
      }
      newAttemptOpt.foreach { attempts =>
        channel.notifyWhenStateChanged(
          currentState,
          () =>
            if (advanced.compareAndSet(false, true)) {
              // currentState has just been left behind - any watchdog scheduled for it is moot.
              pendingWatchdog.foreach(_.cancel())
              monitor(channel.getState(false), attempts)
            })
      }
    }
    monitor(channel.getState(false), 0)
  }

}
