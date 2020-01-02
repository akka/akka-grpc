/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.util.ByteString

sealed trait MetadataEntry
case class StringEntry(value: String) extends MetadataEntry
case class BytesEntry(value: ByteString) extends MetadataEntry
