/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import akka.util.ByteString

object Gzip extends Codec {
  override val name: String = "gzip"

  override def compress(uncompressed: ByteString): ByteString = {
    val baos = new ByteArrayOutputStream(uncompressed.size)
    val gzos = new GZIPOutputStream(baos)
    gzos.write(uncompressed.toArray)
    gzos.flush()
    gzos.close()
    ByteString(baos.toByteArray)
  }

  override def uncompress(compressed: ByteString): ByteString = {
    val gzis = new GZIPInputStream(new ByteArrayInputStream(compressed.toArray))

    val baos = new ByteArrayOutputStream(compressed.size)
    val buffer = new Array[Byte](32 * 1024)
    var read = gzis.read(buffer)
    while (read != -1) {
      baos.write(buffer, 0, read)
      read = gzis.read(buffer)
    }
    ByteString(baos.toByteArray)
  }
}
