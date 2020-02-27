/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.util.{ Map => jMap }

import scala.collection.JavaConverters._
import io.grpc.Status

class GrpcServiceException(val status: Status, val metadata: Map[String, MetadataEntry])
    extends RuntimeException(status.getDescription) {

  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")

  def this(status: Status) {
    this(status, Map[String, MetadataEntry]())
  }

  /**
   * Java API: Constructs a service exception which includes response metadata.
   */
  def this(status: Status, metadata: jMap[String, MetadataEntry]) {
    this(status, metadata.asScala.toMap)
  }

  /**
   * Java API: The response status.
   */
  def getStatus: Status =
    status

  /**
   * Java API: The response headers.
   */
  def getMetadata: jMap[String, MetadataEntry] =
    metadata.asJava

}
