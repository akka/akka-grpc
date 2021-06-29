/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.util.ByteString
import io.grpc.{ Status, StatusException }

object Identity extends Codec {
  override val name = "identity"

  override def compress(bytes: ByteString): ByteString = bytes

  override def uncompress(bytes: ByteString): ByteString = bytes

  override def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString =
    if (compressedBitSet)
      throw new StatusException(
        Status.INTERNAL.withDescription("Compressed-Flag bit is set, but a compression encoding is not specified"))
    else bytes
}
