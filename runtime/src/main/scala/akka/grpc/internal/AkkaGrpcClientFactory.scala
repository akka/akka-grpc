/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.ExecutionContext
import scala.reflect.{ ClassTag, classTag }

import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.AkkaGrpcClient
import akka.stream.Materializer
import scala.reflect.runtime.{ universe => ru }

object AkkaGrpcClientFactory {

  def create[T <: AkkaGrpcClient: ru.TypeTag](settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T = {
    // all this reflection requires:
    //    object @{service.name}Client {
    //      private def create(GrpcClientSettings, Materializer, ExecutionContext): @{service.name}Client
    //    }

    val runtimeMirror = ru.runtimeMirror(getClass.getClassLoader)
    val moduleSymbol = ru.typeOf[T].typeSymbol.companion.asModule
    moduleSymbol.typeSignature.members.foreach(println)
    val targetMethod = moduleSymbol.typeSignature.members
      .find(x => x.isMethod && x.name.toString == "create")
      .get.asMethod
    runtimeMirror
      .reflect(runtimeMirror.reflectModule(moduleSymbol).instance)
      .reflectMethod(targetMethod)(settings, mat, ex)
      .asInstanceOf[T]
  }

  /**
   * A function to create an AkkaGrpcClient, bundling its own configuration.
   * These objects are convenient to pass around as implicit values.
   */
  trait Configured[T <: AkkaGrpcClient] {
    /** Create the gRPC client. */
    def create(): T
  }

  /** Bind configuration to a [[AkkaGrpcClientFactory]], creating a [[Configured]]. */
  def configure[T <: AkkaGrpcClient: ru.TypeTag](
    clientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Configured[T] =
    new Configured[T] {
      def create() = AkkaGrpcClientFactory.create[T](clientSettings)
    }
}
