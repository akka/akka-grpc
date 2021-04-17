/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import akka.Done
import akka.actor.{ CoordinatedShutdown, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.annotation.InternalApi
import akka.grpc.internal.ClientState

import java.util.concurrent.ConcurrentHashMap

/** INTERNAL API */
@InternalApi
private[grpc] final class GrpcImpl(system: ExtendedActorSystem) extends Extension {
  private val clients = new ConcurrentHashMap[ClientState, Unit]

  CoordinatedShutdown(system).addTask("before-actor-system-terminate", "close-grpc-clients") { () =>
    implicit val ec = system.dispatcher
    Future
      .sequence(
        clients
          .keySet()
          .asScala
          .map(client =>
            client.close().recover {
              case e =>
                system.log.error(e, s"Failed to gracefully close $client, proceeding with shutdown anyway")
                Done
            }))
      .map(_ => Done)
  }

  /** INTERNAL API */
  @InternalApi
  def registerClient(client: ClientState): Unit =
    clients.put(client, ())

  /** INTERNAL API */
  @InternalApi
  def deregisterClient(client: ClientState): Unit =
    clients.remove(client)
}

/** INTERNAL API */
@InternalApi
private[grpc] object Grpc extends ExtensionId[GrpcImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GrpcImpl = new GrpcImpl(system)

  override def lookup: ExtensionId[_ <: Extension] = Grpc
}
