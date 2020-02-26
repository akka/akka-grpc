/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.http.scaladsl.model.HttpHeader
import io.grpc.Status

case class GrpcErrorResponse(status: Status, headers: List[HttpHeader] = Nil)
