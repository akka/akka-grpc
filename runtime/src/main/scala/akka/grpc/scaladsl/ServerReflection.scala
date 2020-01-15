/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerReflectionImpl
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange
object ServerReflection {
  @ApiMayChange
  def apply(objects: List[ServiceDescription])(
      implicit mat: Materializer,
      sys: ActorSystem): HttpRequest => scala.concurrent.Future[HttpResponse] =
    ServerReflectionHandler.apply(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
  @ApiMayChange
  def partial(objects: List[ServiceDescription])(
      implicit mat: Materializer,
      sys: ActorSystem): PartialFunction[HttpRequest, scala.concurrent.Future[HttpResponse]] =
    ServerReflectionHandler.partial(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
}
