/*
 * Copyright (C) 2020-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.net.URI
import java.net.InetSocketAddress
import java.util.{ List => JList }
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.collection.immutable
import io.grpc.Attributes
import io.grpc.NameResolver.Listener
import io.grpc.EquivalentAddressGroup
import akka.actor.ActorSystem
import akka.discovery.Lookup
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaDiscoveryNameResolverProviderSpec
    extends TestKit(ActorSystem("AkkaDiscoveryNameResolverProviderSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  "AkkaDiscoveryNameResolverProviderSpec" should {
    "provide a NameResolver that uses the supplied serviceName" in {
      val serviceName = "testServiceName"
      val discovery = new ServiceDiscovery() {
        override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
          lookup.serviceName should be(serviceName)
          Future.successful(Resolved(serviceName, immutable.Seq(ResolvedTarget("10.0.0.3", Some(4312), None))))
        }
      }
      val provider = new AkkaDiscoveryNameResolverProvider(
        discovery,
        443,
        serviceName = serviceName,
        portName = None,
        protocol = None,
        resolveTimeout = 3.seconds,
        None)

      val resolver = provider.newNameResolver(new URI("//" + serviceName), null)

      val addressGroupsPromise = Promise[List[EquivalentAddressGroup]]()
      val listener = new Listener() {
        override def onAddresses(addresses: JList[EquivalentAddressGroup], attributes: Attributes): Unit = {
          import scala.jdk.CollectionConverters._
          addressGroupsPromise.success(addresses.asScala.toList)
        }
        override def onError(error: io.grpc.Status): Unit = ???
      }
      resolver.start(listener)
      val addressGroups = addressGroupsPromise.future.futureValue
      addressGroups.size should be(1)
      val addresses = addressGroups(0).getAddresses()
      addresses.size should be(1)
      val address = addresses.get(0).asInstanceOf[InetSocketAddress]
      address.getHostString() should be("10.0.0.3")
      address.getPort() should be(4312)
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

}
