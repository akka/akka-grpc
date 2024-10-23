/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.grpc.internal.ByteStringUtils
import akka.util.ByteString

import java.io.InputStream

trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
  def deserialize(stream: InputStream): T = deserialize(ByteStringUtils.fromInputStream(stream))
}
