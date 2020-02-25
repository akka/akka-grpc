package akka.grpc

import java.lang.{ Iterable => jIterable }

import akka.grpc.internal.GrpcExceptionHelper
import akka.http.javadsl.model.{ HttpHeader => jHttpHeader }
import akka.http.scaladsl.model.HttpHeader
import io.grpc.Status

class GrpcServiceException(val status: Status, val headers: Seq[HttpHeader])
    extends RuntimeException(status.getDescription) {

  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")

  def this(status: Status) {
    this(status, Nil)
  }

  /**
   * Java API: Constructs a service exception which includes response metadata.
   */
  def this(status: Status, headers: jIterable[jHttpHeader]) {
    this(status, GrpcExceptionHelper.asScala(headers))
  }

  /**
   * Java API: The response status.
   */
  def getStatus: Status =
    status

  /**
   * Java API: The response headers.
   */
  def getHeaders: jIterable[jHttpHeader] =
    GrpcExceptionHelper.asJava(headers)
}
