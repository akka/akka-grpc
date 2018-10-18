/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletionStage, Executor }

import akka.Done
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.grpc.scaladsl.{ RestartingClient => ScalaRestartingClient, AkkaGrpcClient => ScalaAkkaGrpcClient }

import scala.concurrent.{ ExecutionContext, Future }
import scala.compat.java8.FutureConverters._

/**
 * Wraps a Akka gRPC client and restarts it if a [ClientConnectionException] is thrown.
 * All other exceptions result in closing any calls to withClient throwing
 * a [ClientClosedException].
 */
@ApiMayChange
class RestartingClient[T <: AkkaGrpcClient](create: () => T, ec: Executor) extends AkkaGrpcClient {
  private val delegate: ScalaRestartingClient[JavaClientWrapper[T]] = new ScalaRestartingClient[JavaClientWrapper[T]](() => new JavaClientWrapper[T](create()))(ExecutionContext.fromExecutor(ec))

  def withClient[A](f: T => A): A = delegate.withClient(t => f(t.javaClient))
  override def close(): CompletionStage[Done] = delegate.close().toJava
  override def closed(): CompletionStage[Done] = delegate.closed().toJava
}

/**
 * INTERNAL API
 */
@InternalApi
private class JavaClientWrapper[T <: AkkaGrpcClient](val javaClient: T) extends ScalaAkkaGrpcClient {
  /**
   * INTERNAL API
   */
  override def closed(): Future[Done] = javaClient.closed().toScala
  /**
   * INTERNAL API
   */
  override def close(): Future[Done] = javaClient.close().toScala
}

