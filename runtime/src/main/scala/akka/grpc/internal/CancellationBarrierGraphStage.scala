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
    new GraphStageLogic(shape) with InHandler with OutHandler {
      var finished = false
      var pulled = false

      override def onPush(): Unit = {
        pulled = false
        val value = grab(in)
        if (!finished)
          emit(out, value)
      }

      override def onPull(): Unit = {
        pulled = true
        pull(in)
      }

      override def onDownstreamFinish(): Unit = {
        finished = true
        if (!pulled)
          pull(in)
      }

      setHandlers(in, out, this)
    }
}
