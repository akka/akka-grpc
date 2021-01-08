/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import grpc.reflection.v1alpha.reflection.ServerReflection

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ServerReflectionImplSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import ServerReflectionImpl._
  "The Server Reflection implementation utilities" should {
    "split strings up until the next dot" in {
      splitNext("foo.bar") should be(("foo", "bar"))
      splitNext("foo.bar.baz") should be(("foo", "bar.baz"))
    }
    "find a symbol" in {
      containsSymbol("grpc.reflection.v1alpha.ServerReflection", ServerReflection.descriptor) should be(true)
      containsSymbol("grpc.reflection.v1alpha.Foo", ServerReflection.descriptor) should be(false)
      containsSymbol("foo.Foo", ServerReflection.descriptor) should be(false)
    }
  }
}
