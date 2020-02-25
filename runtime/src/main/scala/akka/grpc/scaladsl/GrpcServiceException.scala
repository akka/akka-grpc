package akka.grpc.scaladsl

import akka.http.scaladsl.model.HttpHeader
import io.grpc.Status

class GrpcServiceException(val status: Status, val headers: Seq[HttpHeader] = Nil)
    extends RuntimeException(status.getDescription) {
  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")
}
