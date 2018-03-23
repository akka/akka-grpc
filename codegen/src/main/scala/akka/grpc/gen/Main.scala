/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.JavaServerCodeGenerator
import akka.grpc.gen.scaladsl.ScalaServerCodeGenerator

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
  val out =
    if (req.getParameter.toLowerCase.contains("language=scala")) ScalaServerCodeGenerator.run(req)
    else JavaServerCodeGenerator.run(req)

  System.out.write(out.toByteArray)
  System.out.flush()
}
