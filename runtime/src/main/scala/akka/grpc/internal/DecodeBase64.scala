/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.NotUsed
import akka.annotation.InternalApi
import akka.stream.scaladsl.Flow
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi private[akka] object DecodeBase64 {
  def apply(): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(new DecodeBase64)
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] class DecodeBase64 extends GraphStage[FlowShape[ByteString, ByteString]] {
  private val in = Inlet[ByteString]("DecodeBase64.in")
  private val out = Outlet[ByteString]("DecodeBase64.out")

  override def initialAttributes = Attributes.name("DecodeBase64")

  final override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var buffer: ByteString = ByteString.empty

      override def onPush(): Unit = {
        buffer ++= grab(in)

        val length = buffer.length
        val decodeLength = length - length % 4

        if (decodeLength > 0) {
          val (decodeBytes, remaining) = buffer.splitAt(decodeLength)
          push(out, decodeBytes.decodeBase64)
          buffer = remaining
        } else {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.nonEmpty) {
          if (isAvailable(out)) {
            push(out, buffer.decodeBase64)
            buffer = ByteString.empty
          }
        } else {
          completeStage()
        }
      }

      override def onPull(): Unit = {
        if (isClosed(in)) {
          if (buffer.nonEmpty) {
            push(out, buffer.decodeBase64)
            buffer = ByteString.empty
          } else {
            completeStage()
          }
        } else pull(in)
      }

      setHandlers(in, out, this)
    }
}
