package akka.grpc.scaladsl
import java.nio.charset.StandardCharsets

import com.google.protobuf.util.proto.json_test.{ JsonTestProto, TestAllTypes, TestAny, TestWrappers }
import com.google.protobuf.ByteString
import com.google.protobuf.any.Any
import org.scalatest.matchers.{ MatchResult, Matcher }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalapb.GeneratedMessage
import scalapb.json4s.TypeRegistry

import scala.reflect.ClassTag

class ScalapbJsonProtobufSerializerSpec extends AnyWordSpec with Matchers {

  def serializeTo[T <: GeneratedMessage](json: String)(implicit ser: ScalapbJsonProtobufSerializer[T]): Matcher[T] = {
    message =>
      val serialized = new String(ser.serialize(message).toArray, StandardCharsets.UTF_8)
      MatchResult(
        json == serialized,
        "Serialized JSON does not match expected. Expected:\n{0}\nSerialized:\n{1}\n",
        "Serialized JSON matched:\n{0}\n",
        Array(json, serialized),
        Array(json))
  }

  def roundTrip[T <: GeneratedMessage: ClassTag](implicit ser: ScalapbJsonProtobufSerializer[T]): Matcher[T] = {
    message =>
      val bytes = ser.serialize(message)
      val m2 = ser.deserialize(bytes)
      MatchResult(
        message == m2,
        "Round tripped message does not match. Original:\n{0}\nAfter round trip:\n{1}\n",
        "Round tripped message matches:\n{0}\n",
        Array(message, m2),
        Array(message))
  }

  "Json Serializer" should {
    "serialize wrappers" in {
      val wrappers = TestWrappers(
        boolValue = Some(false),
        int32Value = Some(1),
        int64Value = Some(2),
        uint32Value = Some(3),
        uint64Value = Some(4),
        floatValue = Some(5.0f),
        doubleValue = Some(6.0),
        stringValue = Some("7"),
        bytesValue = Some(ByteString.copyFrom(Array[Byte](8))))

      implicit val ser = new ScalapbJsonProtobufSerializer(TestWrappers.messageCompanion)
      wrappers should serializeTo[TestWrappers](
        """{"int32Value":1,"uint32Value":3,"int64Value":"2","uint64Value":"4","floatValue":5.0,"doubleValue":6.0,"boolValue":false,"stringValue":"7","bytesValue":"CA=="}""")
      wrappers should roundTrip
    }

    "serialize Any" in {
      val any =
        TestAny(anyValue = Some(Any.pack(TestAllTypes(optionalInt32 = 1234))))
      implicit val ser =
        new ScalapbJsonProtobufSerializer(TestAny.messageCompanion, Some(TypeRegistry().addFile(JsonTestProto)))

      any should serializeTo(
        """{"anyValue":{"@type":"type.googleapis.com/json_test.TestAllTypes","optionalInt32":1234}}""")
      any should roundTrip
    }
  }

}
