/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.stream.Attributes
import akka.stream.FanOutShape2
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import akka.util.OptionVal

/**
 * Identity stage that feeds all incoming events to output 1 until it cancels, then switches over to output 2, after
 * completing the materialized value future.
 *
 * INTERNAL API
 * @tparam T
 */
final private[akka] class SwitchOnCancel[T] extends GraphStage[FanOutShape2[T, T, (Throwable, T)]] {

  val in = Inlet[T]("in")
  val mainOut = Outlet[T]("mainOut")
  val failoverOut = Outlet[(Throwable, T)]("failoverOut")

  override def shape: FanOutShape2[T, T, (Throwable, T)] = new FanOutShape2(in, mainOut, failoverOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var failedOver: OptionVal[Throwable] = OptionVal.None

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          failedOver match {
            case OptionVal.Some(error) => push(failoverOut, (error, elem))
            case _                     => push(mainOut, elem)
          }

        }
      })

    setHandler(
      mainOut,
      new OutHandler {
        override def onPull(): Unit =
          pull(in)

        override def onDownstreamFinish(cause: Throwable): Unit = {
          // on downstream cancel or failure switch to second out
          failedOver = OptionVal.Some(cause)
          if (isAvailable(failoverOut) && !hasBeenPulled(in)) {
            pull(in)
          }
        }
      })

    setHandler(
      failoverOut,
      new OutHandler {
        override def onPull(): Unit = {
          // may have been pulled and then failed over
          if (!hasBeenPulled(in)) pull(in)
        }
      })

  }

}
