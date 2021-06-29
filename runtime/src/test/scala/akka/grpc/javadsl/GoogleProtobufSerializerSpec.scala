/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import com.google.protobuf.{ ByteString, Any => ProtobufAny }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GoogleProtobufSerializerSpec extends AnyWordSpec with Matchers {
  "Google protobuf serializer" should {
    "successfully serialize and deserialize a protobuf Any object" in {
      val anySerializer = new GoogleProtobufSerializer(ProtobufAny.parser())

      val obj = ProtobufAny.newBuilder().setTypeUrl("asdf").setValue(ByteString.copyFromUtf8("ASDF")).build()
      val serialized = anySerializer.serialize(obj)
      val deserialized = anySerializer.deserialize(serialized)
      deserialized should be(obj)
    }
  }
}
