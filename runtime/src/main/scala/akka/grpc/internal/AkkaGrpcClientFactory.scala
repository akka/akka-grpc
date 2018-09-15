/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.ExecutionContext
import scala.reflect.{ ClassTag, classTag }

import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.AkkaGrpcClient
import akka.stream.Materializer

object AkkaGrpcClientFactory {
  def create[T <: AkkaGrpcClient : ClassTag](settings: GrpcClientSettings)
      (implicit mat: Materializer, ex: ExecutionContext): T = {
    classTag[T].runtimeClass.asInstanceOf[Class[T]]
        .getConstructor(
          classOf[GrpcClientSettings],
          classOf[Materializer],
          classOf[ExecutionContext],
        )
        .newInstance(settings, mat, ex)
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
  def configure[T <: AkkaGrpcClient : ClassTag](
      clientSettings: GrpcClientSettings,
  )(implicit mat: Materializer, ec: ExecutionContext): Configured[T] =
    () => AkkaGrpcClientFactory.create[T](clientSettings)
}
