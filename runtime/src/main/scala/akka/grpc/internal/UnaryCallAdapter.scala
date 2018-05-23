/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.{CompletableFuture, CompletionStage}

import akka.annotation.InternalApi
import akka.util.OptionVal
import io.grpc._

import scala.concurrent.{Future, Promise}

@InternalApi
private[akka] sealed abstract class UnaryCallAdapter[Res] extends ClientCall.Listener[Res] {
  var headers: OptionVal[Metadata] = OptionVal.None
  // always preceed message
  override def onHeaders(headers: Metadata): Unit = {
    this.headers = OptionVal.Some(headers)
  }

  override def onMessage(message: Res): Unit = {
    // FIXME provide headers to user
    if (!trySuccess(message)) {
      throw Status.INTERNAL.withDescription("More than one value received for unary call")
        .asRuntimeException()
    }
  }

  override def onClose(status: Status, trailers: Metadata): Unit = {
    if (status.isOk) {
      if (!isCompleted)
        // No value received so mark the future as an error
        failure(
          Status.INTERNAL.withDescription("No value received for unary call")
            .asRuntimeException(trailers))
    } else {
      failure(status.asRuntimeException(trailers))
    }
  }

  def isCompleted: Boolean
  def trySuccess(message: Res): Boolean
  def failure(ex: Exception): Unit
}

@InternalApi
private[akka] final class UnaryCallFutureAdapter[Res] extends UnaryCallAdapter[Res] {
  private val promise = Promise[Res]()
  def future: Future[Res] = promise.future

  def isCompleted: Boolean = promise.isCompleted
  def trySuccess(message: Res): Boolean = promise.trySuccess(message)
  def failure(ex: Exception): Unit = promise.tryFailure(ex)
}

@InternalApi
private[akka] final class UnaryCallCSAdapter[Res] extends UnaryCallAdapter[Res] {
  private val completableFuture = new CompletableFuture[Res]()
  def cs: CompletionStage[Res] = completableFuture

  def isCompleted: Boolean = completableFuture.isDone
  def trySuccess(message: Res): Boolean = completableFuture.complete(message)
  def failure(ex: Exception): Unit = completableFuture.completeExceptionally(ex)
}
