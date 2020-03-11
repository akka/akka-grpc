/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.net.{ InetAddress, InetSocketAddress, UnknownHostException }

import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.grpc.GrpcClientSettings
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }
import io.grpc.NameResolver.Listener

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.{ Failure, Success }

class AkkaDiscoveryNameResolver(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    serviceName: String,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration)(implicit val ec: ExecutionContext)
    extends NameResolver {
  override def getServiceAuthority: String = serviceName

  val listener: Promise[Listener] = Promise()

  override def start(l: Listener): Unit = {
    listener.trySuccess(l)
    lookup(l)
  }

  override def refresh(): Unit =
    listener.future.onComplete {
      case Success(l) => lookup(l)
      case Failure(_) => // We never fail this promise
    }

  def lookup(listener: Listener): Unit = {
    discovery.lookup(Lookup(serviceName, portName, protocol), resolveTimeout).onComplete {
      case Success(result) =>
        try {
          listener.onAddresses(addresses(result.addresses), Attributes.EMPTY)
        } catch {
          case e: UnknownHostException =>
            // TODO at least log
            listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
        }
      case Failure(e) =>
        // TODO at least log
        listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
    }
  }

  @throws[UnknownHostException]
  private def addresses(addresses: Seq[ResolvedTarget]) = {
    import scala.collection.JavaConverters._
    addresses
      .map(target => {
        val port = target.port.getOrElse(defaultPort)
        val address = target.address.getOrElse(InetAddress.getByName(target.host))
        new EquivalentAddressGroup(new InetSocketAddress(address, port))
      })
      .asJava
  }

  override def shutdown(): Unit = ()
}

object AkkaDiscoveryNameResolver {
  def apply(settings: GrpcClientSettings)(implicit ec: ExecutionContext): AkkaDiscoveryNameResolver =
    new AkkaDiscoveryNameResolver(
      settings.serviceDiscovery,
      settings.defaultPort,
      settings.serviceName,
      settings.servicePortName,
      settings.serviceProtocol,
      settings.resolveTimeout)
}
