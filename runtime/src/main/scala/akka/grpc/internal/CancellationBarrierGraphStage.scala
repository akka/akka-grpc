/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * 'barrier' that makes sure that, even when downstream is cancelled,
 * the complete upstream is consumed.
 *
 * @tparam T
 */
class CancellationBarrierGraphStage[T] extends GraphStage[FlowShape[T, T]] {
  val in: Inlet[T] = Inlet("CancellationBarrier")
  val out: Outlet[T] = Outlet("CancellationBarrier")

  override val shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = emit(out, grab(in))
      })

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = pull(in)

          override def onDownstreamFinish(): Unit = {
            if (!hasBeenPulled(in))
              pull(in)

            setHandler(in, new InHandler {
              override def onPush(): Unit = {
                grab(in)
                pull(in)
              }
            })
          }
        })
    }
}
