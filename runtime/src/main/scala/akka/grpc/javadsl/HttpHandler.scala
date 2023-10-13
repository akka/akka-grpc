/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.grpc.ServiceDescription
import akka.grpc.internal.HttpTranscoding
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.{ model => jm }
import akka.http.scaladsl.{ model => sm }
import akka.japi.function.{ Function => JFunction }
import akka.stream.Materializer
import com.google.protobuf.Descriptors.{ FileDescriptor => JavaFileDescriptor }
import scalapb.descriptors.{ FileDescriptor => PBFileDescriptor }

import java.util.concurrent.CompletionStage
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.Success

object HttpHandler {
  import scala.compat.java8.FutureConverters._

  private def adaptJFunction(f: JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]])(
      implicit ec: ExecutionContext): PartialFunction[sm.HttpRequest, Future[sm.HttpResponse]] = {
    Function.unlift { request =>
      val futureResponse = f.apply(request).toScala
      futureResponse.value match {
        case Some(Success(response)) if response.status == StatusCodes.NOT_FOUND => None
        case _                                                                   => Some(futureResponse.map(_.asInstanceOf[sm.HttpResponse]))
      }
    }
  }

  private def adaptSFunction(f: PartialFunction[sm.HttpRequest, Future[sm.HttpResponse]])(
      implicit ec: ExecutionContext): JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] = {
    japiFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] { request =>
      f.lift
        .apply(request.asInstanceOf[sm.HttpRequest])
        .map(_.map(_.asInstanceOf[jm.HttpResponse]).toJava)
        .getOrElse(ServiceHandler.notFound)
    }
  }

  private lazy val dependencies: Seq[scalapb.GeneratedFileObject] = Seq(com.google.api.annotations.AnnotationsProto)

  // bcs java code generator implements proto extension in a different way compare to scalapb's generator
  // we can't use neither ScalaPB's generated lens or Java getExtension API for both DSL
  // so instead of using separate API for corresponding DSL, we just reconstruct ScalaPB's class from Java's class
  // see https://github.com/scalapb/ScalaPB/issues/1583 for full discussion
  private def reconstructScalaPBDescriptorFromJavaClass(javaFileDescriptor: JavaFileDescriptor): PBFileDescriptor = {
    val scalaProto =
      com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(javaFileDescriptor.toProto.toByteArray)
    scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, dependencies.map(_.scalaDescriptor))
  }

  @ApiMayChange
  def partial(
      serviceDescription: ServiceDescription,
      grpcHandler: JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]],
      mat: Materializer,
      system: ClassicActorSystemProvider): JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] = {
    implicit val ec: ExecutionContextExecutor = system.classicSystem.dispatcher
    implicit val mati: Materializer = mat
    val javaFileDescriptor = serviceDescription.descriptor
    val pbFileDescriptor = reconstructScalaPBDescriptorFromJavaClass(javaFileDescriptor)
    val sGrpcHandler = adaptJFunction(grpcHandler)
    val handlers = HttpTranscoding.parseRules(javaFileDescriptor, pbFileDescriptor).map {
      case (method, binding) =>
        val sHttpHandler = HttpTranscoding.httpHandler(method, binding, sGrpcHandler)
        adaptSFunction(sHttpHandler)
    }

    ServiceHandler.concat(handlers: _*)
  }
}
