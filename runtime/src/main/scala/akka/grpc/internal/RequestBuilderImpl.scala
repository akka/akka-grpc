/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.{ CompletionStage, TimeUnit }

import akka.NotUsed
import akka.annotation.InternalApi
import akka.dispatch.ExecutionContexts
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JavaSource }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.util.ByteString
import io.grpc._

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// request builder implementations for the generated clients
// note that these are created in generated code in arbitrary user packages
// so must remain public even though internal

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaUnaryRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.scaladsl.SingleResponseRequestBuilder[I, O]
  with OptionsModifications[ScalaUnaryRequestBuilder[I, O]] {

  override def invoke(request: I): Future[O] = {
    val listener = new UnaryCallAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, new Metadata()) // actual metadata already passed through options (?)
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.future
  }

  override def invokeWithMetadata(request: I): Future[GrpcSingleResponse[O]] = {
    val listener = new UnaryCallWithMetadataAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, new Metadata()) // actual metadata already passed through options (?)
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.future
  }

  override def updated(options: CallOptions): ScalaUnaryRequestBuilder[I, O] =
    new ScalaUnaryRequestBuilder[I, O](descriptor, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaUnaryRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.javadsl.SingleResponseRequestBuilder[I, O]
  with OptionsModifications[JavaUnaryRequestBuilder[I, O]] {

  override def invoke(request: I): CompletionStage[O] = {
    val listener = new UnaryCallAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, new Metadata()) // actual metadata already passed through options (?)
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.cs
  }

  override def invokeWithMetadata(request: I): CompletionStage[GrpcSingleResponse[O]] = {
    val listener = new UnaryCallWithMetadataAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, new Metadata()) // actual metadata already passed through options (?)
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.cs
  }

  override def updated(options: CallOptions): JavaUnaryRequestBuilder[I, O] =
    new JavaUnaryRequestBuilder[I, O](descriptor, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaClientStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions,
  materializer: Materializer)
  extends akka.grpc.scaladsl.SingleResponseRequestBuilder[Source[I, NotUsed], O]
  with OptionsModifications[ScalaClientStreamingRequestBuilder[I, O]] {

  override def invoke(request: Source[I, NotUsed]): Future[O] =
    invokeWithMetadata(request).map(_.value)(ExecutionContexts.sameThreadExecutionContext)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Future[GrpcSingleResponse[O]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, false))
    val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
      source.viaMat(flow)(Keep.right)
        .toMat(Sink.head)(Keep.both)
        .run()(materializer)

    metadataFuture.zip(resultFuture).map {
      case (metadata, result) =>
        new GrpcSingleResponse[O] {
          def value: O = result
          def getValue(): O = result
          def headers: Metadata = metadata.headers
          def getHeaders(): Metadata = metadata.getHeaders()
          def trailers: Future[Metadata] = metadata.trailers
          def getTrailers: CompletionStage[Metadata] = metadata.getTrailers()
        }
    }(ExecutionContexts.sameThreadExecutionContext)
  }

  override def updated(options: CallOptions): ScalaClientStreamingRequestBuilder[I, O] =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, materializer)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaClientStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions,
  materializer: Materializer)
  extends akka.grpc.javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O]
  with OptionsModifications[JavaClientStreamingRequestBuilder[I, O]] {

  override def invoke(request: JavaSource[I, NotUsed]): CompletionStage[O] =
    invokeWithMetadata(request).thenApply(_.value)

  override def invokeWithMetadata(source: JavaSource[I, NotUsed]): CompletionStage[GrpcSingleResponse[O]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, false))
    val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
      source.asScala
        .viaMat(flow)(Keep.right)
        .toMat(Sink.head)(Keep.both)
        .run()(materializer)

    metadataFuture.zip(resultFuture).map {
      case (metadata, result) =>
        new GrpcSingleResponse[O] {
          def value: O = result
          def getValue(): O = result
          def headers: Metadata = metadata.headers
          def getHeaders(): Metadata = metadata.getHeaders()
          def trailers: Future[Metadata] = metadata.trailers
          def getTrailers: CompletionStage[Metadata] = metadata.getTrailers()
        }
    }(ExecutionContexts.sameThreadExecutionContext).toJava
  }

  override def updated(options: CallOptions): JavaClientStreamingRequestBuilder[I, O] =
    new JavaClientStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, materializer)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaServerStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.scaladsl.StreamResponseRequestBuilder[I, O]
  with OptionsModifications[ScalaServerStreamingRequestBuilder[I, O]] {

  override def invoke(request: I): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: I): Source[O, Future[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true))
    Source.single(source).viaMat(flow)(Keep.right)
  }

  override def updated(options: CallOptions): ScalaServerStreamingRequestBuilder[I, O] =
    new ScalaServerStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaServerStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.javadsl.StreamResponseRequestBuilder[I, O]
  with OptionsModifications[JavaServerStreamingRequestBuilder[I, O]] {

  override def invoke(request: I): JavaSource[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: I): JavaSource[O, CompletionStage[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true))
    Source.single(source).viaMat(flow)(Keep.right).mapMaterializedValue(_.toJava).asJava
  }

  override def updated(options: CallOptions): JavaServerStreamingRequestBuilder[I, O] =
    new JavaServerStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaBidirectionalStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.scaladsl.StreamResponseRequestBuilder[Source[I, NotUsed], O]
  with OptionsModifications[ScalaBidirectionalStreamingRequestBuilder[I, O]] {

  override def invoke(request: Source[I, NotUsed]): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Source[O, Future[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true))
    source.viaMat(flow)(Keep.right)
  }

  override def updated(options: CallOptions): ScalaBidirectionalStreamingRequestBuilder[I, O] =
    new ScalaBidirectionalStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaBidirectionalStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  val options: CallOptions)
  extends akka.grpc.javadsl.StreamResponseRequestBuilder[JavaSource[I, NotUsed], O]
  with OptionsModifications[JavaBidirectionalStreamingRequestBuilder[I, O]] {

  override def invoke(request: JavaSource[I, NotUsed]): JavaSource[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: JavaSource[I, NotUsed]): JavaSource[O, CompletionStage[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true))
    source.asScala.viaMat(flow)(Keep.right).mapMaterializedValue(_.toJava).asJava
  }

  override def updated(options: CallOptions): JavaBidirectionalStreamingRequestBuilder[I, O] =
    new JavaBidirectionalStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options)

}

/**
 * INTERNAL API
 */
@InternalApi
trait OptionsModifications[T <: OptionsModifications[T]] {
  def options: CallOptions
  def updated(options: CallOptions): T

  def addMetadata(key: String, value: String): T = {
    // FIXME Key is instance equal to allow for replacing of the same key but still also allowing multiple
    // values with the same name-key, not sure if it is important we support that
    updated(options = options.withOption(CallOptions.Key.of[String](key, null), value))
  }

  def addMetadata(key: String, value: ByteString): T = {
    // FIXME Key is instance equal to allow for replacing of the same key but still also allowing multiple
    // values with the same name-key, not sure if it is important we support that
    updated(options = options.withOption(CallOptions.Key.of[Array[Byte]](key, null), value.toArray))
  }

  def withDeadline(deadline: FiniteDuration): T = {
    updated(options = options.withDeadline(Deadline.after(deadline.length, deadline.unit)))
  }

  def withDeadline(deadline: java.time.Duration): T = {
    updated(options = options.withDeadline(Deadline.after(deadline.toMillis, TimeUnit.MILLISECONDS)))
  }
}