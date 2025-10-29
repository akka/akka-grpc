/*
 * Copyright (C) 2020-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.annotation.InternalApi

import java.net.{ InetAddress, InetSocketAddress, UnknownHostException }
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.event.Logging
import akka.grpc.GrpcClientSettings
import io.grpc.{ Attributes, EquivalentAddressGroup, NameResolver, Status }
import io.grpc.NameResolver.Listener

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.{ Failure, Success }

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class AkkaDiscoveryNameResolver(
    discovery: ServiceDiscovery,
    defaultPort: Int,
    serviceName: String,
    portName: Option[String],
    protocol: Option[String],
    resolveTimeout: FiniteDuration,
    refreshInterval: Option[FiniteDuration])(implicit val ec: ExecutionContext, system: ActorSystem)
    extends NameResolver {

  private final val log = Logging(system, "akka.grpc.internal.AkkaDiscoveryNameResolver")

  override def getServiceAuthority: String = serviceName

  private val listener: Promise[Listener] = Promise()

  // initialized after first resolve if needed
  private val refreshTask = new AtomicReference[Cancellable]

  override def start(l: Listener): Unit = {
    log.debug("Name resolver for {} started", serviceName)
    listener.trySuccess(l)
    lookup(l, evict = false)
  }

  override def refresh(): Unit = refresh(false)

  private def refresh(evict: Boolean): Unit =
    listener.future.onComplete {
      case Success(l) =>
        log.debug("Name resolver for {} refreshing", serviceName)
        lookup(l, evict)
      case Failure(_) => // We never fail this promise
    }

  def lookup(listener: Listener, evict: Boolean): Unit = {
    val request = {
      val l = Lookup(serviceName, portName, protocol)
      if (evict) l.withDiscardCache
      else l
    }
    val result = discovery.lookup(request, resolveTimeout)

    result.onComplete {
      case Success(result) =>
        try {
          if (log.isDebugEnabled)
            log.debug(
              "Successful service discovery for service {}, found addresses: {}",
              serviceName,
              result.addresses.mkString(", "))
          listener.onAddresses(addresses(result.addresses), Attributes.EMPTY)
        } catch {
          case e: UnknownHostException =>
            log.warning(e, "Unknown host for service {}", serviceName)
            listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
        }
      case Failure(e) =>
        log.warning(e, "Service discovery failed for service {}", serviceName)
        listener.onError(Status.UNKNOWN.withDescription(e.getMessage))
    }

    // initialize refresh timer after first lookup, if configured
    if (refreshInterval.isDefined && refreshTask.get() == null) {
      result.onComplete { _ =>
        refreshInterval.foreach { interval =>
          val cancellable = system.scheduler.scheduleWithFixedDelay(interval, interval)(() => refresh(evict = true))
          if (!refreshTask.compareAndSet(null, cancellable)) {
            // concurrent update beat us to it, there already is a scheduled task
            cancellable.cancel()
          }
        }
      }
    }
  }

  @throws[UnknownHostException]
  private def addresses(addresses: Seq[ResolvedTarget]) = {
    import scala.jdk.CollectionConverters._
    addresses
      .map(target => {
        val port = target.port.getOrElse(defaultPort)
        val address = target.address.getOrElse(InetAddress.getByName(target.host))
        new EquivalentAddressGroup(new InetSocketAddress(address, port))
      })
      .asJava
  }

  override def shutdown(): Unit = {
    val refreshCancellable = refreshTask.get()
    if (refreshCancellable ne null) refreshCancellable.cancel()
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object AkkaDiscoveryNameResolver {
  def apply(
      settings: GrpcClientSettings)(implicit ec: ExecutionContext, system: ActorSystem): AkkaDiscoveryNameResolver =
    new AkkaDiscoveryNameResolver(
      settings.serviceDiscovery,
      settings.defaultPort,
      settings.serviceName,
      settings.servicePortName,
      settings.serviceProtocol,
      settings.resolveTimeout,
      settings.discoveryRefreshInterval)
}
