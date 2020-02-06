package akka.grpc.internal

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaDiscoveryNameResolverProviderSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers {
  "AkkaDiscoveryNameResolverProviderSpec" should {
    "provide a NameResolver that uses the supplied serviceName" in {
//      AkkaDiscoveryNameResolver("")
    }
  }

}
