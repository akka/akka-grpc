/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.{ JavaClientCodeGenerator, JavaInterfaceCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.javadsl.play.{ PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaClientCodeGenerator, ScalaServerCodeGenerator, ScalaTraitCodeGenerator }
import akka.grpc.gen.scaladsl.play.{ PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator }

// This is the protoc plugin that the gradle plugin uses
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

  private val generatePlay: Boolean = reqLowerCase.contains("generate_play=true")

  val LogFileRegex = """(?:.*,)logfile=([^,]+)(?:,.*)?""".r
  private val logger = req.getParameter match {
    case LogFileRegex(path) => new FileLogger(path)
    case _ => SilencedLogger
  }

  val out = {
    val codeGenerators =
      if (!generatePlay) {
        if (languageScala) {
          // Scala
          if (generateClient && generateServer) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator, ScalaServerCodeGenerator)
          else if (generateClient) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator)
          else if (generateServer) Seq(ScalaTraitCodeGenerator, ScalaServerCodeGenerator)
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        } else {
          // Java
          if (generateClient && generateServer) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator)
          else if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator)
          else if (generateServer) Seq(JavaInterfaceCodeGenerator, JavaServerCodeGenerator)
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        }
      } else {
        if (languageScala) {
          // Scala
          if (generateClient && generateServer) Seq(ScalaTraitCodeGenerator, PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator)
          else if (generateClient) Seq(ScalaTraitCodeGenerator, PlayScalaClientCodeGenerator)
          else if (generateServer) Seq(ScalaTraitCodeGenerator, PlayScalaServerCodeGenerator)
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        } else {
          // Java
          if (generateClient && generateServer) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator)
          else if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, PlayJavaClientCodeGenerator)
          else if (generateServer) Seq(JavaInterfaceCodeGenerator, PlayJavaServerCodeGenerator)
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        }
      }

    codeGenerators.foreach { g =>
      val gout = g.run(req, logger)
      System.out.write(gout.toByteArray)
      System.out.flush()
    }
  }
}
