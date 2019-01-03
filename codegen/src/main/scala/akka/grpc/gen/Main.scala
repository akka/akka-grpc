/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.JavaBothCodeGenerator
import akka.grpc.gen.javadsl.JavaClientCodeGenerator
import akka.grpc.gen.javadsl.JavaServerCodeGenerator
import akka.grpc.gen.javadsl.play.{ PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator
import akka.grpc.gen.scaladsl.ScalaClientCodeGenerator
import akka.grpc.gen.scaladsl.ScalaServerCodeGenerator
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
    val codeGenerator =
      if (!generatePlay) {
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
      } else {
        if (languageScala) {
          // Scala
          if (generateClient && generateServer) CombinedPlayScalaBothCodeGenerator
          else if (generateClient) CombinedPlayScalaClientCodeGenerator
          else if (generateServer) CombinedPlayScalaServerCodeGenerator
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        } else {
          // Java
          if (generateClient && generateServer) CombinedPlayJavaBothCodeGenerator
          else if (generateClient) CombinedPlayJavaClientCodeGenerator
          else if (generateServer) CombinedPlayJavaServerCodeGenerator
          else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
        }
      }

    codeGenerator.run(req, logger)
  }

  System.out.write(out.toByteArray)
  System.out.flush()

  /**
   * Generators to generate both the 'plain' akka-grpc code and the play-specific code that depends on it.
   * In other build tools (Maven and sbt) these are passed to protoc separately, but since Gradle does the
   * akka-grpc code generation in a single protoc invocation we need to combine those here:
   */
  object CombinedPlayJavaClientCodeGenerator extends PlayJavaClientCodeGenerator with JavaClientCodeGenerator {
    override def name = "combined-play-java-client"
  }
  object CombinedPlayJavaServerCodeGenerator extends PlayJavaServerCodeGenerator with JavaServerCodeGenerator {
    override def name = "combined-play-java-server"
  }
  object CombinedPlayJavaBothCodeGenerator extends PlayJavaClientCodeGenerator with JavaClientCodeGenerator with PlayJavaServerCodeGenerator with JavaServerCodeGenerator {
    override def name = "combined-play-java-both"
  }
  object CombinedPlayScalaClientCodeGenerator extends PlayScalaClientCodeGenerator with ScalaClientCodeGenerator {
    override def name = "combined-play-scala-client"
  }
  object CombinedPlayScalaServerCodeGenerator extends PlayScalaServerCodeGenerator with ScalaServerCodeGenerator {
    override def name = "combined-play-scala-server"
  }
  object CombinedPlayScalaBothCodeGenerator extends PlayScalaClientCodeGenerator with ScalaClientCodeGenerator with PlayScalaServerCodeGenerator with ScalaServerCodeGenerator {
    override def name = "combined-play-scala-both"
  }
}
