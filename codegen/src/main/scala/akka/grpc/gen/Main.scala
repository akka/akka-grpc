/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.ByteArrayOutputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import akka.grpc.gen.javadsl.{ JavaClientCodeGenerator, JavaInterfaceCodeGenerator, JavaPowerApiInterfaceCodeGenerator, JavaServerCodeGenerator }
import akka.grpc.gen.javadsl.play.{ PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator }
import akka.grpc.gen.scaladsl.{ ScalaClientCodeGenerator, ScalaPowerApiTraitCodeGenerator, ScalaServerCodeGenerator, ScalaTraitCodeGenerator }
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

  private val serverPowerApis: Boolean = reqLowerCase.contains("server_power_apis=true")

  private val usePlayActions: Boolean = reqLowerCase.contains("use_play_actions=true")

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
          val base =
            if (generateClient && generateServer) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator, ScalaServerCodeGenerator(serverPowerApis))
            else if (generateClient) Seq(ScalaTraitCodeGenerator, ScalaClientCodeGenerator)
            else if (generateServer) Seq(ScalaTraitCodeGenerator, ScalaServerCodeGenerator(serverPowerApis))
            else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
          if (serverPowerApis) Seq(ScalaPowerApiTraitCodeGenerator) ++ base
          else base
        } else {
          // Java
          val base =
            if (generateClient && generateServer) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, JavaServerCodeGenerator(serverPowerApis))
            else if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator)
            else if (generateServer) Seq(JavaInterfaceCodeGenerator, JavaServerCodeGenerator(serverPowerApis))
            else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
          if (serverPowerApis) Seq(JavaPowerApiInterfaceCodeGenerator) ++ base
          else base
        }
      } else {
        if (languageScala) {
          // Scala
          val base =
            if (generateClient && generateServer) Seq(ScalaTraitCodeGenerator, PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator(serverPowerApis, usePlayActions))
            else if (generateClient) Seq(ScalaTraitCodeGenerator, PlayScalaClientCodeGenerator)
            else if (generateServer) Seq(ScalaTraitCodeGenerator, PlayScalaServerCodeGenerator(serverPowerApis, usePlayActions))
            else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
          if (serverPowerApis) Seq(ScalaPowerApiTraitCodeGenerator) ++ base
          else base
        } else {
          // Java
          val base =
            if (generateClient && generateServer) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, PlayJavaClientCodeGenerator, PlayJavaServerCodeGenerator(serverPowerApis, usePlayActions))
            else if (generateClient) Seq(JavaInterfaceCodeGenerator, JavaClientCodeGenerator, PlayJavaClientCodeGenerator)
            else if (generateServer) Seq(JavaInterfaceCodeGenerator, PlayJavaServerCodeGenerator(serverPowerApis, usePlayActions))
            else throw new IllegalArgumentException("At least one of generateClient or generateServer must be enabled")
          if (serverPowerApis) Seq(JavaPowerApiInterfaceCodeGenerator) ++ base
          else base
        }
      }

    codeGenerators.foreach { g =>
      val gout = g.run(req, logger)
      System.out.write(gout.toByteArray)
      System.out.flush()
    }
  }
}
