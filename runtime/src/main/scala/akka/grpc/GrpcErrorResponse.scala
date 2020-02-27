package akka.grpc

import java.util.{ Map => jMap }
import scala.collection.JavaConverters._
import io.grpc.Status

case class GrpcErrorResponse(status: Status, metadata: Map[String, MetadataEntry]) {
  def this(status: Status) {
    this(status, Map[String, MetadataEntry]())
  }

  /**
   * Java API: Create an error response from status and metadata.
   */
  def this(status: Status, metadata: jMap[String, MetadataEntry]) {
    this(status, metadata.asScala.toMap)
  }
}

object GrpcErrorResponse {
  def apply(status: Status): GrpcErrorResponse =
    new GrpcErrorResponse(status)
}
