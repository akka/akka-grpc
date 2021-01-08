/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.util.ByteString

abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString
  def uncompress(bytes: ByteString): ByteString
}
