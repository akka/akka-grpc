package akka.grpc.internal

import java.util

import akka.grpc.GrpcServiceException
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }

import scala.concurrent.Promise

class NameResolverListenerProbe extends NameResolver.Listener {
  private val promise = Promise[Seq[EquivalentAddressGroup]]

  override def onAddresses(servers: util.List[EquivalentAddressGroup], attributes: Attributes): Unit = {
    import scala.collection.JavaConverters._
    promise.trySuccess(servers.asScala)
  }

  override def onError(error: Status): Unit =
    promise.tryFailure(new GrpcServiceException(error))

  val future = promise.future
}
