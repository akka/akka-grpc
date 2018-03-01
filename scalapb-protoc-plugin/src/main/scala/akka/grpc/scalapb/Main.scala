package akka.grpc.scalapb

import java.io.{ BufferedOutputStream, ByteArrayOutputStream }

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.http.grpc._
import com.google.protobuf.compiler.plugin.CodeGeneratorResponse

import scalapb.ScalaPbCodeGenerator

object Main extends App {

  val inBytes: Array[Byte] = {
    val baos = new ByteArrayOutputStream(math.max(64, System.in.available()))
    val buffer = Array.ofDim[Byte](32 * 1024)

    var bytesRead = System.in.read(buffer)
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead)
      bytesRead = System.in.read(buffer)
    }
    baos.toByteArray
  }

  val outBytes = ScalaPbCodeGenerator.run(inBytes)

  val bos = new BufferedOutputStream(System.out)
  bos.write(outBytes)
  bos.flush()
}
