package akka.http.grpc

import io.grpc.Status

class GrpcServiceException(val status: Status) extends RuntimeException(status.getDescription) {
  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")
}