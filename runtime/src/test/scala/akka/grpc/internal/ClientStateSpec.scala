/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.scaladsl.Source
import io.grpc.{ CallOptions, MethodDescriptor }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClientStateSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {
  implicit val sys = ActorSystem()
  implicit val ec = sys.dispatcher
  implicit val patience = PatienceConfig(timeout = 10.seconds, interval = 150.milliseconds)

  private def clientState(channelCompletion: Promise[Done] = Promise[Done]()) = {
    val channel =
      new InternalChannel() {
        override def invoke[I, O](
            request: I,
            headers: MetadataImpl,
            descriptor: MethodDescriptor[I, O],
            options: CallOptions): Future[O] = ???
        override def invokeWithMetadata[I, O](
            request: I,
            headers: MetadataImpl,
            descriptor: MethodDescriptor[I, O],
            options: CallOptions): Future[GrpcSingleResponse[O]] = ???
        override def invokeWithMetadata[I, O](
            source: Source[I, NotUsed],
            fqMethodName: String,
            headers: MetadataImpl,
            descriptor: MethodDescriptor[I, O],
            streamingResponse: Boolean,
            options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = ???
        override def shutdown(): Unit = channelCompletion.success(Done)
        override def done: Future[Done] = channelCompletion.future
      }
    new ClientState(channel)
  }

  "Client State" should {
    "successfully provide a channel" in {
      // given a state
      val state = clientState()
      // it provides a channel when needed
      state.internalChannel should not be null
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }
}
