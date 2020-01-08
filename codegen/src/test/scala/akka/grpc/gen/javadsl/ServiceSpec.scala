/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import org.scalatest.WordSpec
import org.scalatest.Matchers

class ServiceSpec extends WordSpec with Matchers {
  "The Service model" should {
    "correctly camelcase strings" in {
      Service.toCamelCase("foo_bar") should be("FooBar")
    }
    "correctly determine basenames" in {
      Service.basename("helloworld.proto") should be("helloworld")
      Service.basename("grpc/testing/metrics.proto") should be("metrics")
    }
  }
}
