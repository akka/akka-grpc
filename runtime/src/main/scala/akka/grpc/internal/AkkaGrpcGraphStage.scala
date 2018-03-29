/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.annotation.InternalApi
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import io.grpc.stub.StreamObserver

@InternalApi
private class AkkaGrpcGraphStage[I, O](name: String, operator: StreamObserver[O] => StreamObserver[I]) extends GraphStage[FlowShape[I, O]] {

  val in = Inlet[I](name + ".in")
  val out = Outlet[O](name + ".out")

  override val shape: FlowShape[I, O] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      val onNextCb = getAsyncCallback[O](value => emit(out, value))

      val failCb = getAsyncCallback[Throwable](t ⇒ failStage(t))

      val responseObserver = new StreamObserver[O] {
        override def onError(t: Throwable) = failCb.invoke(t)
        override def onCompleted() = getAsyncCallback[Unit](_ => complete(out)).invoke(())
        override def onNext(value: O) = onNextCb.invoke(value)
      }

      val requestObserver = operator(responseObserver)

      override def preStart(): Unit = pull(in)

      override def onPush(): Unit = {
        val input = grab(in)
        requestObserver.onNext(input)
        pull(in)
      }

      override def onUpstreamFinish(): Unit = requestObserver.onCompleted()

      override def onUpstreamFailure(t: Throwable): Unit = requestObserver.onError(t)

      override def onPull(): Unit = ()

      setHandlers(in, out, this)
    }
}
