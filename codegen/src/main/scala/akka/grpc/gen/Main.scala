/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator

object Main extends App {

  val inBytes: Array[Byte] = {
    val baos = new ByteArrayOutputStream(math.max(64, System.in.available()))
    val buffer = new Array[Byte](32 * 1024)

    var bytesRead = System.in.read(buffer)
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead)
      bytesRead = System.in.read(buffer)
    }
    baos.toByteArray
  }

  val req = CodeGeneratorRequest.parseFrom(inBytes)
  // TODO #155 use a parameter to define whether to generate code for
  // the client, the server, or both
  val out =
    if (req.getParameter.toLowerCase.contains("language=scala")) ScalaBothCodeGenerator.run(req)
    else JavaBothCodeGenerator.run(req)

  System.out.write(out.toByteArray)
  System.out.flush()
}
