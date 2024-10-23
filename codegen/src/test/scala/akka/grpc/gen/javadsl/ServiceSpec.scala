/*
 * Copyright (C) 2020-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ServiceSpec extends AnyWordSpec with Matchers {
  "The Service model" should {
    "correctly camelcase strings" in {
      Service.toCamelCase("foo_bar") should be("FooBar")
      Service.toCamelCase("grpc-example") should be("GrpcExample")
      Service.toCamelCase("grpc02example") should be("Grpc02Example")
    }
    "correctly determine basenames" in {
      Service.basename("helloworld.proto") should be("helloworld")
      Service.basename("grpc/testing/metrics.proto") should be("metrics")
    }
  }
}
