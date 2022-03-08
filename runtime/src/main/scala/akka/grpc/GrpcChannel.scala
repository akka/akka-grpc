/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.grpc.internal.{ ChannelUtils, InternalChannel }
import akka.grpc.scaladsl.Grpc

class GrpcChannel(val settings: GrpcClientSettings, @InternalApi val internalChannel: InternalChannel)(
    implicit sys: ClassicActorSystemProvider) {

  Grpc(sys).registerChannel(this)

  def closeCS(): CompletionStage[Done] =
    close().toJava

  def closedCS(): CompletionStage[Done] =
    closed().toJava

  def close(): Future[akka.Done] = {
    Grpc(sys).deregisterChannel(this)
    ChannelUtils.close(internalChannel)
  }

  def closed(): Future[akka.Done] =
    internalChannel.done
}

object GrpcChannel {
  def apply(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider): GrpcChannel = {
    new GrpcChannel(
      settings,
      ChannelUtils.create(settings, akka.event.Logging(sys.classicSystem, classOf[GrpcChannel])))
  }
}
