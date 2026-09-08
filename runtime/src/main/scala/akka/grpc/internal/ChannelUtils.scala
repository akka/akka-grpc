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
import scala.util.control.NonFatal

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

      // Whichever of the watchdog below or the state-change callback further down fires first
      // for this CONNECTING episode wins; the other becomes a no-op, guarded by this flag.
      val advanced = new AtomicBoolean(false)

      // grpc-java has no timeout for the CONNECTING state as a whole (see grpc/grpc-java#1943), so
      // a connection stuck mid-handshake would otherwise never reach TRANSIENT_FAILURE and never
      // count as a failed attempt.
      val pendingWatchdog: Option[Cancellable] =
        if (currentState != ConnectivityState.CONNECTING) None
        else
          connectingTimeout match {
            case timeout: FiniteDuration =>
              Some(
                scheduleOnce(
                  timeout,
                  () =>
                    // Order matters: check state before claiming `advanced`, else a spurious win
                    // here silences the real callback too. Also racy with enterIdle() below (no
                    // atomic "abandon only if still stuck" primitive) - worst case forces a
                    // just-READY connection back to IDLE, harmlessly.
                    if (channel.getState(false) == ConnectivityState.CONNECTING && advanced
                        .compareAndSet(false, true)) {
                      log.warning(
                        "gRPC client channel has been CONNECTING for more than {}, abandoning the stuck " +
                        "connection attempt and starting a new one (see akka.grpc.client config setting " +
                        "'connecting-timeout')",
                        timeout)
                      try {
                        failOrKeepGoing(connectionAttempts + 1).foreach { attempts =>
                          channel.enterIdle()
                          monitor(channel.getState(true), attempts)
                        }
                      } catch {
                        case NonFatal(e) =>
                          // `advanced` is already claimed, so the real callback won't run either -
                          // without this, a failure here would hang the client forever, unsignalled.
                          log.error(e, "Failed to abandon a connection stuck CONNECTING, giving up")
                          val ex = new ClientConnectionException("Unable to establish connection: " + e.getMessage)
                          ex.initCause(e)
                          ready.tryFailure(ex) || done.tryFailure(ex)
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
