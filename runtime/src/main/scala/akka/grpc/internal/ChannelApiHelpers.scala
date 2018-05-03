/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.{ CompletableFuture, Executor }
import com.google.common.util.concurrent.{ FutureCallback, Futures, ListenableFuture }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{ GraphStage, GraphStageLogic, _ }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.annotation.InternalApi
import io.grpc.stub.StreamObserver

/**
 * INTERNAL API
 * Include some helpers to convert types from Channel API to Scala / Akka Streams API
 */
@InternalApi
object ChannelApiHelpers {

  /**
   * INTERNAL API
   *
   * Converts a Guava [[ListenableFuture]] to a Scala [[Future]]
   */
  @InternalApi
  def toScalaFuture[A](guavaFuture: ListenableFuture[A])(implicit ec: ExecutionContext): Future[A] = {

    val p = Promise[A]()
    val callback = new FutureCallback[A] {
      override def onFailure(t: Throwable): Unit = p.failure(t)
      override def onSuccess(a: A): Unit = p.success(a)
    }

    val javaExecutor = ec match {
      case exc: Executor => exc // Akka Dispatcher is an Executor
      case _ =>
        new Executor {
          override def execute(command: Runnable): Unit = ec.execute(command)
        }
    }

    Futures.addCallback(guavaFuture, callback, javaExecutor)
    p.future
  }

  /**
   * INTERNAL API
   *
   * Builds a akka stream [[Flow]] from a function `StreamObserver[O] => StreamObserver[I]`
   */
  @InternalApi
  def buildFlow[I, O](name: String)(operator: StreamObserver[O] => StreamObserver[I]): Flow[I, O, NotUsed] =
    Flow.fromGraph(new AkkaGrpcGraphStage(name, operator))
}
