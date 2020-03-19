/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.io.InputStream

import akka.util.ByteString

trait ProtobufSerializer[T] {
  val format: ProtobufSerialization

  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T = deserialize(bytes.iterator.asInputStream)
  def deserialize(bytes: InputStream): T
}
