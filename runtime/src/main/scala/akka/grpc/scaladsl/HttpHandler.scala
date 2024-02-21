/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.annotation.ApiMayChange
import akka.grpc.internal.HttpTranscoding
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import com.google.protobuf.Descriptors.{ FileDescriptor => JavaFileDescriptor }
import scalapb.descriptors.{ FileDescriptor => PBFileDescriptor }

import scala.concurrent.{ ExecutionContext, Future }

object HttpHandler {

  @ApiMayChange
  def partial(
      pbFileDescriptor: PBFileDescriptor,
      javaFileDescriptor: JavaFileDescriptor,
      grpcHandler: PartialFunction[HttpRequest, Future[HttpResponse]])(
      implicit mat: Materializer,
      ec: ExecutionContext): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    val handlers = HttpTranscoding.parseRules(javaFileDescriptor, pbFileDescriptor).map {
      case (method, binding) => HttpTranscoding.httpHandler(method, binding, grpcHandler)
    }
    ServiceHandler.concat(handlers: _*)
  }

}
