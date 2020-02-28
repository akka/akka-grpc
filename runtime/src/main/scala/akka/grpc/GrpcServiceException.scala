/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import io.grpc.Status

import akka.grpc.scaladsl.{ Metadata, MetadataBuilder }
import akka.grpc.internal.JavaMetadataImpl

class GrpcServiceException(val status: Status, val metadata: Metadata) extends RuntimeException(status.getDescription) {

  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")

  def this(status: Status) {
    this(status, MetadataBuilder.empty)
  }

  /**
   * Java API: Constructs a service exception which includes response metadata.
   */
  def this(status: Status, metadata: javadsl.Metadata) {
    this(status, metadata.asScala)
  }

  /**
   * Java API: The response status.
   */
  def getStatus: Status =
    status

  /**
   * Java API: The response metadata.
   */
  def getMetadata: javadsl.Metadata =
    new JavaMetadataImpl(metadata)

}
