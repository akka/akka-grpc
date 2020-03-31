/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerReflectionImpl
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
object ServerReflection {
  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def apply(objects: List[ServiceDescription])(
      implicit sys: ActorSystem): HttpRequest => scala.concurrent.Future[HttpResponse] =
    ServerReflectionHandler.apply(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))

  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def partial(objects: List[ServiceDescription])(
      implicit sys: ActorSystem): PartialFunction[HttpRequest, scala.concurrent.Future[HttpResponse]] =
    ServerReflectionHandler.partial(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
}
