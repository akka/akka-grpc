/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util

import akka.grpc.GrpcServiceException
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }

import scala.concurrent.Promise

class NameResolverListenerProbe extends NameResolver.Listener {
  private val promise = Promise[Seq[EquivalentAddressGroup]]()

  override def onAddresses(servers: util.List[EquivalentAddressGroup], attributes: Attributes): Unit = {
    import scala.jdk.CollectionConverters._
    promise.trySuccess(servers.asScala.toSeq)
  }

  override def onError(error: Status): Unit =
    promise.tryFailure(new GrpcServiceException(error))

  val future = promise.future
}
