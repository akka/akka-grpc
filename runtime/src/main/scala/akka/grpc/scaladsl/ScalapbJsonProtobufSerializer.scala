package akka.grpc.scaladsl
import java.io.InputStream

import akka.grpc.{ ProtobufSerialization, ProtobufSerializer }
import akka.grpc.ProtobufSerialization.Json
import akka.util.ByteString
import org.json4s.jackson.JsonMethods
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }
import scalapb.json4s.{ JsonFormat, TypeRegistry }

class ScalapbJsonProtobufSerializer[T <: GeneratedMessage](
    companion: GeneratedMessageCompanion[T],
    registry: Option[TypeRegistry] = None)
    extends ProtobufSerializer[T] {

  private val typeRegistry = registry.getOrElse(TypeRegistry().addMessageByCompanion(companion))
  private val printer = JsonFormat.printer.withTypeRegistry(typeRegistry)
  private val parser = JsonFormat.parser.withTypeRegistry(typeRegistry)

  override val format: ProtobufSerialization = Json
  override def serialize(t: T): ByteString = {
    val bs = ByteString.newBuilder
    JsonMethods.mapper.writeValue(bs.asOutputStream, printer.toJson(t))
    bs.result
  }

  override def deserialize(bytes: InputStream): T = parser.fromJson(JsonMethods.parse(bytes))(companion)
}
