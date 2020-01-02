/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scalapb

import java.io.ByteArrayOutputStream

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

  System.out.write(outBytes)
  System.out.flush()
}
