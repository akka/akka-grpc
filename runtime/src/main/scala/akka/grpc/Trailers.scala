/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.annotation.ApiMayChange
import io.grpc.Status
import akka.grpc.internal.JavaMetadataImpl
import akka.grpc.scaladsl.{ Metadata, MetadataBuilder }
import akka.grpc.javadsl.{ Metadata => jMetadata }

@ApiMayChange
class Trailers(val status: Status, val metadata: Metadata) {
  def this(status: Status) = {
    this(status, MetadataBuilder.empty)
  }

  def this(status: Status, metadata: jMetadata) = {
    this(status, metadata.asScala)
  }

  /**
   * Java API: Returns the status.
   */
  def getStatus: Status =
    status

  /**
   * Java API: Returns the trailing metadata.
   */
  def getMetadata: jMetadata =
    new JavaMetadataImpl(metadata)
}

object Trailers {
  def apply(status: Status): Trailers = new Trailers(status)
  def apply(status: Status, metadata: Metadata): Trailers = new Trailers(status, metadata)
}
