package akka.http.grpc.scaladsl

import akka.http.grpc.ProtobufSerializer
import akka.util.ByteString
import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

class ScalapbProtobufSerializer[T <: GeneratedMessage with Message[T]](companion: GeneratedMessageCompanion[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T) = ByteString(companion.toByteArray(t))
  override def deserialize(bytes: ByteString): T = companion.parseFrom(bytes.iterator.asInputStream)
}