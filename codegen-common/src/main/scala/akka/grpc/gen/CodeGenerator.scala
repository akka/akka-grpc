package akka.grpc.gen

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scala.collection.JavaConverters._

/**
 * Code generator trait that is not directly bound to scala-pb or protoc (other than the types).
 */
trait CodeGenerator {

  /** Generator name; example: `akka-grpc-scala` */
  def name: String

  final def run(request: Array[Byte]): Array[Byte] = {
    val req = CodeGeneratorRequest.parseFrom(request)

    val res = run(req)
    res.toByteArray
  }

  def run(request: CodeGeneratorRequest): CodeGeneratorResponse

}
