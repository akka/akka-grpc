/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.Done
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/**
 * INTERNAL API
 *
 * Public as is included in generated code.
 */
@InternalApi
trait AkkaGrpcClient {
  def close(): Future[Done]
  def closed(): Future[Done]
}

object AkkaGrpcClientFactory {
  def create[T <: AkkaGrpcClient: ClassTag](settings: GrpcClientSettings)(implicit mat: Materializer, ex: ExecutionContext): T = {
    implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      .getConstructor(classOf[GrpcClientSettings], classOf[Materializer], classOf[ExecutionContext])
      .newInstance(settings, mat, ex)
  }

  /**
   * A function to create an AkkaGrpcClient, bundling its own configuration. These objects are convenient to
   * pass around as implicit values.
   */
  trait Configured[T <: AkkaGrpcClient] {
    /** Create the gRPC client. */
    def create(): T
  }

  /**
   * Bind configuration to a [[AkkaGrpcClientFactory]], creating a [[Configured]].
   */
  def configure[T <: AkkaGrpcClient: ClassTag](
    clientSettings: GrpcClientSettings)(implicit
    materializer: Materializer,
    executionContext: ExecutionContext): Configured[T] =
    new Configured[T] {
      override def create(): T = AkkaGrpcClientFactory.create[T](clientSettings)
    }
}

/**
 * INTERNAL API
 *
 * Public as is included in generated code.
 */
@InternalApi
trait JavaAkkaGrpcClient {
  def close(): CompletionStage[Done]
  def closed(): CompletionStage[Done]
}
