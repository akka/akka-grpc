/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.DoNotInherit
import akka.util.ByteString

/**
 * Represents metadata entry.
 */
@DoNotInherit
trait MetadataEntry

/**
 * Represents a text metadata entry.
 */
@DoNotInherit
trait StringEntry extends MetadataEntry {
  def getValue(): String
}

/**
 * Represents a binary metadata entry.
 */
@DoNotInherit
trait BytesEntry extends MetadataEntry {
  def getValue(): ByteString
}
