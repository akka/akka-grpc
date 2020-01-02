/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.ExecutionContext
import scala.reflect.{ classTag, ClassTag }
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.AkkaGrpcClient
import akka.stream.Materializer
import language.reflectiveCalls

object AkkaGrpcClientFactory {
  def create[T <: AkkaGrpcClient: ClassTag](
      settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T = {
    // this reflection requires:
    //    object @{service.name}Client {
    //      def apply(GrpcClientSettings)(Materializer, ExecutionContext): @{service.name}Client
    //    }
    val classT: Class[_] = classTag[T].runtimeClass
    val module: AnyRef = getClass.getClassLoader.loadClass(classT.getName + "$").getField("MODULE$").get(null)
    val instance = module
      .asInstanceOf[{ def apply(settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T }]
    instance(settings)(mat, ex)
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
  def configure[T <: AkkaGrpcClient: ClassTag](
      clientSettings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext): Configured[T] =
    new Configured[T] {
      def create() = AkkaGrpcClientFactory.create[T](clientSettings)
    }
}
