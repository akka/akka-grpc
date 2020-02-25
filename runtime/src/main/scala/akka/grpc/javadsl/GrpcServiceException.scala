package akka.grpc.javadsl

import akka.http.javadsl.model.HttpHeader
import io.grpc.Status
import java.lang.{ Iterable => jIterable }
import java.util

class GrpcServiceException(val status: Status, val headers: jIterable[HttpHeader])
    extends RuntimeException(status.getDescription) {
  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")

  def this(status: Status) {
    this(status, new util.ArrayList[HttpHeader]())
  }
}
