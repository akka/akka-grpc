/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse }
import akka.http.Http2Bridge
import akka.http.scaladsl.model.{ AttributeKey, HttpRequest, HttpResponse, RequestResponseAssociation }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.OptionVal
import com.github.ghik.silencer.silent
import io.grpc.{ CallOptions, MethodDescriptor }

import scala.concurrent.{ Future, Promise }

/**
 * INTERNAL API
 */
@InternalApi
object AkkaHttpClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  @silent("never used")
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    implicit val ec = sys.classicSystem.dispatcher

    val donePromise = Promise[Done]()
    // TODO FIXME discovery/loadbalancing/...?
    val host = settings.serviceName

    val queue =
      Source
        .queue[HttpRequest](4242, OverflowStrategy.fail)
        .via(Http2Bridge.connect(host, settings.defaultPort))
        .to(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })
        .run()

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val p = Promise[HttpResponse]()
      queue.offer(request.addAttribute(ResponsePromise.Key, ResponsePromise(p))).flatMap(_ => p.future)
    }

    new InternalChannel() {
      override def shutdown(): Unit = ???
      override def done: Future[Done] = donePromise.future

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
          source: I,
          defaultFlow: OptionVal[Flow[I, O, Future[GrpcResponseMetadata]]],
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = ???

      override def createFlow[I, O](
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Flow[I, O, Future[GrpcResponseMetadata]] = ???
    }
  }

  case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
  object ResponsePromise {
    val Key = AttributeKey[ResponsePromise]("association-handle")
  }
}
