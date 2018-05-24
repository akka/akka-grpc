/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.{ CompletableFuture, CompletionStage }

import akka.NotUsed
import akka.annotation.InternalApi
import akka.grpc.RequestBuilder
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.javadsl.{ Flow => JavaFlow, Sink => JavaSink, Source => JavaSource }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.util.{ ByteString, OptionVal }
import io.grpc.stub.{ ClientCalls, StreamObserver }
import io.grpc._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.compat.java8.FutureConverters._
/**
 * INTERNAL API
 */
@InternalApi
final case class RequestBuilderImpl[Req, Res, Ret](
  options: CallOptions,
  delegatedExecute: (Req, CallOptions) => Ret) extends RequestBuilder[Req, Ret]() {

  def addMetadata(key: String, value: String): RequestBuilderImpl[Req, Res, Ret] = {
    // FIXME Key is instance equal to allow for replacing of the same key but still also allowing multiple
    // values with the same name-key, not sure if it is important we support that
    copy(options = options.withOption(CallOptions.Key.of[String](key, null), value))
  }

  def addMetadata(key: String, value: ByteString): RequestBuilderImpl[Req, Res, Ret] = {
    // FIXME Key is instance equal to allow for replacing of the same key but still also allowing multiple
    // values with the same name-key, not sure if it is important we support that
    copy(options = options.withOption(CallOptions.Key.of[Array[Byte]](key, null), value.toArray))
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
      val listener = new UnaryCallFutureAdapter[Res]
      val call = channel.newCall(descriptor, options)
      call.start(listener, new Metadata()) // actual metadata already passed through options (?)
      call.sendMessage(req)
      call.halfClose()
      call.request(1)
      listener.future
    }

    RequestBuilderImpl[Req, Res, Future[Res]](options, invoke)
  }

  def unaryJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    channel: Channel,
    options: CallOptions, ec: ExecutionContext): RequestBuilder[Req, CompletionStage[Res]] = {

    def invoke(req: Req, options: CallOptions): CompletionStage[Res] = {
      val listener = new UnaryCallCSAdapter[Res]
      val call = channel.newCall(descriptor, options)
      call.start(listener, new Metadata()) // actual metadata already passed through options (?)
      call.sendMessage(req)
      call.halfClose()
      call.request(1)
      listener.cs
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
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, false))
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
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, false))
        .mapMaterializedValue(future => future.toJava)
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
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, true))
      Source.single(req).via(flow)
    }

    RequestBuilderImpl[Req, Res, Source[Res, NotUsed]](options, invoke)
  }

  def serverStreamingJava[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[Req, JavaSource[Res, NotUsed]] = {

    def invoke(req: Req, options: CallOptions): JavaSource[Res, NotUsed] = {
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, true))
        .mapMaterializedValue(future => future.toJava)
      val bufferSize = options.getOption(CallOptions.Key.of("buffer_size", 10000))
      JavaSource.single(req).via(flow)
    }

    RequestBuilderImpl[Req, Res, JavaSource[Res, NotUsed]](options, invoke)
  }

  def bidirectional[Req, Res](
    descriptor: MethodDescriptor[Req, Res],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions): RequestBuilder[Source[Req, NotUsed], Source[Res, NotUsed]] = {

    def invoke(reqs: Source[Req, NotUsed], options: CallOptions): Source[Res, NotUsed] = {
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, true))
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
      val flow = Flow.fromGraph(new NewAkkaGrpcGraphStage(descriptor, fqMethodName, channel, options, true))
        .mapMaterializedValue(future => future.toJava)
      reqs.via(flow)
    }

    RequestBuilderImpl[JavaSource[Req, NotUsed], Res, JavaSource[Res, NotUsed]](options, invoke)
  }

}