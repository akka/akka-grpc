package akka.grpc.javadsl
import java.io.{ InputStream, InputStreamReader, OutputStreamWriter }
import java.nio.charset.StandardCharsets

import akka.grpc.{ ProtobufSerialization, ProtobufSerializer }
import akka.grpc.ProtobufSerialization.Json
import akka.util.ByteString
import com.google.protobuf.TypeRegistry
import com.google.protobuf.util.JsonFormat

class GoogleJsonProtobufSerializer[T <: com.google.protobuf.Message](defaultInstance: T) extends ProtobufSerializer[T] {

  private val typeRegistry: TypeRegistry = TypeRegistry.newBuilder().add(defaultInstance.getDescriptorForType).build()
  private val printer = JsonFormat.printer().usingTypeRegistry(typeRegistry)
  private val parser = JsonFormat.parser().usingTypeRegistry(typeRegistry)

  override val format: ProtobufSerialization = Json

  override def serialize(t: T): ByteString = {
    val bs = ByteString.newBuilder
    val out = new OutputStreamWriter(bs.asOutputStream, StandardCharsets.UTF_8)
    printer.appendTo(t, out)
    out.close() // JsonFormat does not flush, so force flush of char encoding buffer
    bs.result
  }

  override def deserialize(bytes: InputStream): T = {
    val bld = defaultInstance.newBuilderForType
    parser.merge(new InputStreamReader(bytes, StandardCharsets.UTF_8), bld)
    bld.build.asInstanceOf[T]
  }
}
