/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings

import io.grpc.{ ConnectivityState, ManagedChannel }

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
        NettyClientUtils.createChannel(settings, log)(sys.classicSystem.dispatcher)
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
   */
  @InternalApi
  private[akka] def monitorChannel(
      ready: Promise[Unit],
      done: Promise[Done],
      channel: ManagedChannel,
      maxConnectionAttempts: Option[Int],
      log: LoggingAdapter): Unit = {
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

        case ConnectivityState.IDLE | ConnectivityState.CONNECTING =>
          Some(connectionAttempts)
      }
      newAttemptOpt.foreach { attempts =>
        channel.notifyWhenStateChanged(currentState, () => monitor(channel.getState(false), attempts))
      }
    }
    monitor(channel.getState(false), 0)
  }

}
