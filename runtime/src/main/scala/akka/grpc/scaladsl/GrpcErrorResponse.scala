package akka.grpc.scaladsl

import akka.http.scaladsl.model.HttpHeader
import io.grpc.Status

case class GrpcErrorResponse(status: Status, headers: Seq[HttpHeader] = Nil)
