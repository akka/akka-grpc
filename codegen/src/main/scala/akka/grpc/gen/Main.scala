/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import akka.grpc.gen.javadsl.JavaClientCodeGenerator
import akka.grpc.gen.javadsl.JavaServerCodeGenerator
import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator
import akka.grpc.gen.scaladsl.ScalaClientCodeGenerator
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
  private val reqLowerCase = req.getParameter.toLowerCase

  private val languageScala: Boolean = reqLowerCase.contains("language=scala")

  private val generateClient: Boolean = !reqLowerCase.contains("generate_client=false")

  private val generateServer: Boolean = !reqLowerCase.contains("generate_server=false")

  val out = {
    val codeGenerator =
      if (languageScala) {
        // Scala
        if (generateClient && generateServer) ScalaBothCodeGenerator
        else if (generateClient) ScalaClientCodeGenerator
        else if (generateServer) ScalaServerCodeGenerator
        else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
      } else {
        // Java
        if (generateClient && generateServer) JavaBothCodeGenerator
        else if (generateClient) JavaClientCodeGenerator
        else if (generateServer) JavaServerCodeGenerator
        else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
      }

    codeGenerator.run(req)
  }

  System.out.write(out.toByteArray)
  System.out.flush()
}
