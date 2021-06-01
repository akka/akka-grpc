/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import com.google.protobuf.any.{ Any => ScalapbAny }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.google.protobuf.ByteString

class ScalapbProtobufSerializerSpec extends AnyWordSpec with Matchers {
  "Google protobuf serializer" should {
    "successfully serialize and deserialize a protobuf Any object" in {
      val anySerializer = new ScalapbProtobufSerializer(ScalapbAny)

      val obj = ScalapbAny("asdf", ByteString.copyFromUtf8("ASDF"))
      val serialized = anySerializer.serialize(obj)
      val deserialized = anySerializer.deserialize(serialized)
      deserialized should be(obj)
    }
  }
}
