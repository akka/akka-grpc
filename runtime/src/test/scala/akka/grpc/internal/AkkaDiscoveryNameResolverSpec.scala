package akka.grpc.internal

import java.net.{ InetSocketAddress, URI }

import akka.actor.ActorSystem
import akka.grpc.{ GrpcClientSettings, GrpcServiceException }
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.JavaConverters._

class AkkaDiscoveryNameResolverSpec
    extends TestKit(ActorSystem())
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {
  implicit val ex = system.dispatcher

  "The AkkaDiscovery-backed NameResolver" should {
    "correctly report an error for an unknown hostname" in {
      val host = "example.invalid"
      val resolver = AkkaDiscoveryNameResolver(GrpcClientSettings.connectToServiceAt(host, 80))
      val probe = new NameResolverListenerProbe()

      resolver.start(probe)

      val exception = probe.future.failed.futureValue.asInstanceOf[GrpcServiceException]
      exception.status.getDescription should equal(host + ": Name or service not known")
    }

    "support serving a static host/port" in {
      // Unfortunately it needs to be an actually resolvable address...
      val host = "akka.io"
      val port = 4040
      val resolver = AkkaDiscoveryNameResolver(GrpcClientSettings.connectToServiceAt(host, port))
      val probe = new NameResolverListenerProbe()

      resolver.start(probe)

      val addresses = probe.future.futureValue match {
        case Seq(addressGroup) => addressGroup.getAddresses
        case _                 => fail("Expected a single address group")
      }
      addresses.asScala match {
        case Seq(address: InetSocketAddress) =>
          address.getPort should be(port)
          address.getAddress.getHostName should be(host)
        case other =>
          fail(s"Expected a single InetSocketAddress, got $other")
      }
    }
  }
}
