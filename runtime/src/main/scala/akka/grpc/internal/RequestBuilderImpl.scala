/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.NotUsed
import akka.annotation.InternalApi
import akka.grpc.RequestBuilder
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.javadsl.{ Flow => JavaFlow, Sink => JavaSink, Source => JavaSource }
import akka.stream.{ Materializer, OverflowStrategy }
import io.grpc.stub.{ ClientCalls, StreamObserver }
import io.grpc.{ CallOptions, Channel, Deadline, MethodDescriptor }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 */
@InternalApi
final case class RequestBuilderImpl[Req, Res, Ret](
  options: CallOptions,
  delegatedExecute: (Req, CallOptions) => Ret) extends RequestBuilder[Req, Ret]() {

  def addMetadata(key: String, value: String): RequestBuilderImpl[Req, Res, Ret] = {
    // FIXME support values other than string values?
    copy(options = options.withOption(CallOptions.Key.of(key, ""), value))
  }

  def withDeadline(deadline: FiniteDuration): RequestBuilderImpl[Req, Res, Ret] = {
    copy(options = options.withDeadline(Deadline.after(deadline.length, deadline.unit)))
  }

  def invoke(req: Req): Ret = delegatedExecute(req, options)

}

/**
 * Factory methods for the generated client code to build requests
 *
 * INTERNAL API
 */
@InternalApi
object RequestBuilderImpl {

  def unary[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    channel: Channel,
    options: CallOptions)(implicit ec: ExecutionContext): RequestBuilder[Req, Future[Res]] = {

    def invoke(req: Req, options: CallOptions): Future[Res] = {
      ChannelApiHelpers.toScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(descriptor, options), req))
    }

    RequestBuilderImpl[Req, Res, Future[Res]](options, invoke)
  }

  def unaryJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    channel: Channel,
    options: CallOptions, ec: ExecutionContext): RequestBuilder[Req, CompletionStage[Res]] = {

    def invoke(req: Req, options: CallOptions): CompletionStage[Res] = {
      JavaChannelApiHelpers.toCompletableFuture(
        ClientCalls.futureUnaryCall(channel.newCall(descriptor, options), req), ec)
    }

    RequestBuilderImpl[Req, Res, CompletionStage[Res]](options, invoke)
  }

  def clientStreaming[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions)(
    implicit
    materializer: Materializer): RequestBuilder[Source[Req, NotUsed], Future[Res]] = {

    def invoke(req: Source[Req, NotUsed], options: CallOptions): Future[Res] = {
      // FIXME backpressure
      val flow =
        ChannelApiHelpers.buildFlow[Req, Res](fqMethodName) { responseObserver =>
          ClientCalls.asyncClientStreamingCall(
            channel.newCall(descriptor, options),
            responseObserver)
        }
      req.via(flow).runWith(Sink.head)
    }

    RequestBuilderImpl[Source[Req, NotUsed], Res, Future[Res]](options, invoke)
  }

  def clientStreamingJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions,
    materializer: Materializer): RequestBuilder[JavaSource[Req, NotUsed], CompletionStage[Res]] = {
    def invoke(req: JavaSource[Req, NotUsed], options: CallOptions): CompletionStage[Res] = {
      // FIXME backpressure
      val flow =
        JavaChannelApiHelpers.buildFlow[Req, Res](fqMethodName, responseObserver =>
          ClientCalls.asyncClientStreamingCall(
            channel.newCall(descriptor, options),
            responseObserver))

      req.via(flow).runWith(JavaSink.head[Res](), materializer)
    }

    RequestBuilderImpl[JavaSource[Req, NotUsed], Res, CompletionStage[Res]](options, invoke)
  }

  def serverStreaming[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[Req, Source[Res, NotUsed]] = {

    def invoke(req: Req, options: CallOptions): Source[Res, NotUsed] = {
      val flow = ChannelApiHelpers.buildFlow[Req, Res](fqMethodName) { responseObserver =>
        new StreamObserver[Req] {
          override def onError(t: Throwable): Unit = responseObserver.onError(t)

          override def onCompleted(): Unit = ()

          override def onNext(request: Req): Unit =
            ClientCalls.asyncServerStreamingCall(
              channel.newCall(descriptor, options),
              request,
              responseObserver)
        }
      }

      // FIXME backpressure
      val bufferSize = options.getOption(CallOptions.Key.of("buffer_size", 10000))
      Source.single(req)
        // channel calls don't support back-pressure so we need to buffered it
        // and eventually fail the stream
        .via(flow.buffer(bufferSize, OverflowStrategy.fail))

    }

    RequestBuilderImpl[Req, Res, Source[Res, NotUsed]](options, invoke)
  }

  def serverStreamingJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[Req, JavaSource[Res, NotUsed]] = {

    def invoke(req: Req, options: CallOptions): JavaSource[Res, NotUsed] = {
      val flow = JavaChannelApiHelpers.buildFlow[Req, Res](fqMethodName, { responseObserver =>
        new StreamObserver[Req] {
          override def onError(t: Throwable): Unit = responseObserver.onError(t)

          override def onCompleted(): Unit = ()

          override def onNext(request: Req): Unit =
            ClientCalls.asyncServerStreamingCall(
              channel.newCall(descriptor, options),
              request,
              responseObserver)
        }
      })

      // FIXME backpressure
      val bufferSize = options.getOption(CallOptions.Key.of("buffer_size", 10000))
      JavaSource.single(req)
        // channel calls don't support back-pressure so we need to buffered it
        // and eventually fail the stream
        .via(flow.buffer(bufferSize, OverflowStrategy.fail))

    }

    RequestBuilderImpl[Req, Res, JavaSource[Res, NotUsed]](options, invoke)
  }

  def bidirectional[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[Source[Req, NotUsed], Source[Res, NotUsed]] = {

    def invoke(reqs: Source[Req, NotUsed], options: CallOptions): Source[Res, NotUsed] = {
      // FIXME backpressure
      val flow =
        ChannelApiHelpers.buildFlow[Req, Res](descriptor.getFullMethodName) { responseObserver =>
          ClientCalls.asyncBidiStreamingCall(
            channel.newCall(descriptor, options),
            responseObserver)
        }
      reqs.via(flow)
    }

    RequestBuilderImpl[Source[Req, NotUsed], Res, Source[Res, NotUsed]](options, invoke)
  }

  def bidirectionalJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[JavaSource[Req, NotUsed], JavaSource[Res, NotUsed]] = {

    def invoke(reqs: JavaSource[Req, NotUsed], options: CallOptions): JavaSource[Res, NotUsed] = {
      // FIXME backpressure
      val flow =
        JavaChannelApiHelpers.buildFlow[Req, Res](descriptor.getFullMethodName, responseObserver =>
          ClientCalls.asyncBidiStreamingCall(
            channel.newCall(descriptor, options),
            responseObserver))
      reqs.via(flow)
    }

    RequestBuilderImpl[JavaSource[Req, NotUsed], Res, JavaSource[Res, NotUsed]](options, invoke)
  }

}