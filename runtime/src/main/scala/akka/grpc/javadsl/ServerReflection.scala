/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.Collection
import java.util.concurrent.CompletionStage

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.ServerReflectionImpl
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

@ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
object ServerReflection {
  @ApiMayChange(issue = "https://github.com/akka/akka-grpc/issues/850")
  def create(
      objects: Collection[ServiceDescription],
      sys: ClassicActorSystemProvider): akka.japi.function.Function[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.collection.JavaConverters._
    val delegate = ServerReflectionHandler.apply(
      ServerReflectionImpl(objects.asScala.map(_.descriptor).toSeq, objects.asScala.map(_.name).toList))(sys)
    import scala.compat.java8.FutureConverters._
    implicit val ec = sys.classicSystem.dispatcher
    request =>
      delegate
        .apply(request.asInstanceOf[akka.http.scaladsl.model.HttpRequest])
        .map(_.asInstanceOf[HttpResponse])
        .toJava
  }
}
