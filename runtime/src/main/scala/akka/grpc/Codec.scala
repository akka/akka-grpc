/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.util.ByteString

// TODO we could also try to use akka.http.scaladsl.coding, but that might be overkill since that is geared
// towards streaming use cases, and we need to compress each message separately.
abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString
  def uncompress(bytes: ByteString): ByteString
}
