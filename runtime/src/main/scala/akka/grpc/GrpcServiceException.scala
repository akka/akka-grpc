/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import io.grpc.Status

class GrpcServiceException(val status: Status) extends RuntimeException(status.getDescription) {
  require(!status.isOk, "Use GrpcServiceException in case of failure, not as a flow control mechanism.")
}
