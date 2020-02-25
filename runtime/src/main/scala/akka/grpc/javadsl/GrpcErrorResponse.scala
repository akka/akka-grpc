/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.lang.{ Iterable => jIterable }
import io.grpc.Status
import akka.http.javadsl.model.HttpHeader

case class GrpcErrorResponse(status: Status, headers: jIterable[HttpHeader])
