package akka.http.grpc

import akka.util.ByteString

trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
}
