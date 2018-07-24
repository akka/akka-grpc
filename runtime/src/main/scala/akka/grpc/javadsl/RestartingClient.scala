/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletionStage, Executor }

import akka.Done
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.grpc.internal.{ AkkaGrpcClient, JavaAkkaGrpcClient }
import akka.grpc.scaladsl.{ RestartingClient => ScalaRestartingClient }

import scala.concurrent.{ ExecutionContext, Future }
import scala.compat.java8.FutureConverters._

/**
 * Wraps a Akka gRPC  client and restarts it if a [ClientConnectionException] is thrown.
 * All other exceptions result in closing and any calls to withClient throwing
 * a [ClientClosedException].
 */
@ApiMayChange
class RestartingClient[T <: JavaAkkaGrpcClient](create: () => T, ec: Executor) {
  private val delegate: ScalaRestartingClient[JavaClientWrapper[T]] = new ScalaRestartingClient[JavaClientWrapper[T]](() => new JavaClientWrapper[T](create()))(ExecutionContext.fromExecutor(ec))

  def withClient[A](f: T => A): A = delegate.withClient(t => f(t.javaClient))
  def close(): CompletionStage[Done] = delegate.close().toJava

}

/**
 * INTERNAL API
 */
@InternalApi
private class JavaClientWrapper[T <: JavaAkkaGrpcClient](val javaClient: T) extends AkkaGrpcClient {
  /**
   * INTERNAL API
   */
  override def closed(): Future[Done] = javaClient.closed().toScala
  /**
   * INTERNAL API
   */
  override def close(): Future[Done] = javaClient.close().toScala
}

