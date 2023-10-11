/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import io.grpc.{ Status, StatusRuntimeException }
import akka.annotation.ApiMayChange
import akka.grpc.scaladsl.{ Metadata, MetadataBuilder }
import akka.grpc.internal.{ GrpcMetadataImpl, JavaMetadataImpl, RichGrpcMetadataImpl }
import com.google.protobuf.any.Any
import io.grpc.protobuf.StatusProto

import scala.jdk.CollectionConverters._

object GrpcServiceException {

  /**
   * Java API
   */
  def create(
      code: com.google.rpc.Code,
      message: String,
      details: java.util.List[scalapb.GeneratedMessage]): GrpcServiceException = {
    apply(code, message, details.asScala.toVector)
  }

  /**
   * Scala API
   */
  def apply(
      code: com.google.rpc.Code,
      message: String,
      details: Seq[scalapb.GeneratedMessage]): GrpcServiceException = {

    val status = com.google.rpc.Status.newBuilder().setCode(code.getNumber).setMessage(message)

    details.foreach(msg => status.addDetails(toJavaProto(Any.pack(msg))))

    val statusRuntimeException = StatusProto.toStatusRuntimeException(status.build)

    new GrpcServiceException(
      statusRuntimeException.getStatus,
      new GrpcMetadataImpl(
        // might not be present
        Option(statusRuntimeException.getTrailers).getOrElse(new io.grpc.Metadata())))
  }

  private def toJavaProto(scalaPbSource: com.google.protobuf.any.Any): com.google.protobuf.Any = {
    val javaPbOut = com.google.protobuf.Any.newBuilder
    javaPbOut.setTypeUrl(scalaPbSource.typeUrl)
    javaPbOut.setValue(scalaPbSource.value)
    javaPbOut.build
  }

  def apply(ex: StatusRuntimeException): GrpcServiceException = {
    new GrpcServiceException(
      ex.getStatus,
      new RichGrpcMetadataImpl(
        ex.getStatus,
        // might not be present
        Option(ex.getTrailers).getOrElse(new io.grpc.Metadata())))
  }
}

@ApiMayChange
class GrpcServiceException(val status: Status, val metadata: Metadata)
    extends StatusRuntimeException(status, metadata.raw.orNull) {

  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")

  def this(status: Status) = {
    this(status, MetadataBuilder.empty)
  }

  /**
   * Java API: Constructs a service exception which includes response metadata.
   */
  def this(status: Status, metadata: javadsl.Metadata) = {
    this(status, metadata.asScala)
  }

  /**
   * Java API: The response metadata.
   */
  def getMetadata: javadsl.Metadata =
    new JavaMetadataImpl(metadata)

}
