/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.Collection
import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerReflectionImpl
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange
object ServerReflection {
  @ApiMayChange
  def create(
      objects: Collection[ServiceDescription],
      mat: Materializer,
      sys: ActorSystem): akka.japi.Function[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.collection.JavaConverters._
    val delegate = ServerReflectionHandler.apply(
      ServerReflectionImpl(objects.asScala.map(_.descriptor).toSeq, objects.asScala.map(_.name).toList))(mat, sys)
    import scala.compat.java8.FutureConverters._
    implicit val ec = sys.dispatcher
    request =>
      delegate
        .apply(request.asInstanceOf[akka.http.scaladsl.model.HttpRequest])
        .map(_.asInstanceOf[HttpResponse])
        .toJava
  }
}
