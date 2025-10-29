/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.annotation.{ ApiMayChange, DoNotInherit }
import akka.util.ByteString

/**
 * Represents metadata entry.
 */
@DoNotInherit
@ApiMayChange
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
