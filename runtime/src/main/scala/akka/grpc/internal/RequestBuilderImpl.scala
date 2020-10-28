/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.annotation.{ InternalApi, InternalStableApi }
import akka.dispatch.ExecutionContexts
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JavaSource }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import io.grpc._

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import akka.grpc.GrpcClientSettings
import com.github.ghik.silencer.silent

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaUnaryRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ec: ExecutionContext)
    extends akka.grpc.scaladsl.SingleResponseRequestBuilder[I, O]
    with MetadataOperations[ScalaUnaryRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: I): Future[O] =
    channel.invoke(request, headers, descriptor, defaultOptions)

  override def invokeWithMetadata(request: I): Future[GrpcSingleResponse[O]] =
    channel.invokeWithMetadata(request, headers, descriptor, callOptionsWithDeadline())

  override def withHeaders(headers: MetadataImpl): ScalaUnaryRequestBuilder[I, O] =
    new ScalaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaUnaryRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ex: ExecutionContext)
    extends akka.grpc.javadsl.SingleResponseRequestBuilder[I, O]
    with MetadataOperations[JavaUnaryRequestBuilder[I, O]] {
  private val delegate = new ScalaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  override def invoke(request: I): CompletionStage[O] =
    delegate.invoke(request).toJava

  override def invokeWithMetadata(request: I): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request).toJava

  override def withHeaders(headers: MetadataImpl): JavaUnaryRequestBuilder[I, O] =
    new JavaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaClientStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit mat: Materializer, ec: ExecutionContext)
    extends akka.grpc.scaladsl.SingleResponseRequestBuilder[Source[I, NotUsed], O]
    with MetadataOperations[ScalaClientStreamingRequestBuilder[I, O]] {

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: Source[I, NotUsed]): Future[O] =
    invokeWithMetadata(request).map(_.value)(ExecutionContexts.parasitic)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Future[GrpcSingleResponse[O]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val src =
      channel.invokeWithMetadata[I, O](source, headers, descriptor, false, callOptionsWithDeadline())
    val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
      src.toMat(Sink.head)(Keep.both).run()

    metadataFuture
      .zip(resultFuture)
      .map {
        case (metadata, result) =>
          new GrpcSingleResponse[O] {
            def value: O = result
            def getValue(): O = result
            def headers = metadata.headers
            def getHeaders() = metadata.getHeaders()
            def trailers = metadata.trailers
            def getTrailers = metadata.getTrailers()
          }
      }(ExecutionContexts.parasitic)
  }

  override def withHeaders(headers: MetadataImpl): ScalaClientStreamingRequestBuilder[I, O] =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaClientStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit mat: Materializer, ec: ExecutionContext)
    extends akka.grpc.javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O]
    with MetadataOperations[JavaClientStreamingRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private val delegate =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)

  override def invoke(request: JavaSource[I, NotUsed]): CompletionStage[O] =
    delegate.invoke(request.asScala).toJava

  override def invokeWithMetadata(request: JavaSource[I, NotUsed]): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request.asScala).toJava

  override def withHeaders(headers: MetadataImpl): JavaClientStreamingRequestBuilder[I, O] =
    new JavaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaServerStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ec: ExecutionContext)
    extends akka.grpc.scaladsl.StreamResponseRequestBuilder[I, O]
    with MetadataOperations[ScalaServerStreamingRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: I): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(request: I): Source[O, Future[GrpcResponseMetadata]] =
    channel.invokeWithMetadata(Source.single(request), headers, descriptor, true, callOptionsWithDeadline())

  override def withHeaders(headers: MetadataImpl): ScalaServerStreamingRequestBuilder[I, O] =
    new ScalaServerStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaServerStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ec: ExecutionContext)
    extends akka.grpc.javadsl.StreamResponseRequestBuilder[I, O]
    with MetadataOperations[JavaServerStreamingRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private val delegate =
    new ScalaServerStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)

  override def invoke(request: I): JavaSource[O, NotUsed] =
    delegate.invoke(request).asJava

  override def invokeWithMetadata(source: I): JavaSource[O, CompletionStage[GrpcResponseMetadata]] =
    delegate.invokeWithMetadata(source).mapMaterializedValue(_.toJava).asJava

  override def withHeaders(headers: MetadataImpl): JavaServerStreamingRequestBuilder[I, O] =
    new JavaServerStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaBidirectionalStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ec: ExecutionContext)
    extends akka.grpc.scaladsl.StreamResponseRequestBuilder[Source[I, NotUsed], O]
    with MetadataOperations[ScalaBidirectionalStreamingRequestBuilder[I, O]] {

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: Source[I, NotUsed]): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Source[O, Future[GrpcResponseMetadata]] =
    channel.invokeWithMetadata(source, headers, descriptor, true, callOptionsWithDeadline())

  override def withHeaders(headers: MetadataImpl): ScalaBidirectionalStreamingRequestBuilder[I, O] =
    new ScalaBidirectionalStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaBidirectionalStreamingRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl)(implicit ec: ExecutionContext)
    extends akka.grpc.javadsl.StreamResponseRequestBuilder[JavaSource[I, NotUsed], O]
    with MetadataOperations[JavaBidirectionalStreamingRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      @silent("never used") fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private val delegate =
    new ScalaBidirectionalStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)

  override def invoke(request: JavaSource[I, NotUsed]): JavaSource[O, NotUsed] =
    delegate.invoke(request.asScala).asJava

  override def invokeWithMetadata(
      source: JavaSource[I, NotUsed]): JavaSource[O, CompletionStage[GrpcResponseMetadata]] =
    delegate.invokeWithMetadata(source.asScala).mapMaterializedValue(_.toJava).asJava

  override def withHeaders(headers: MetadataImpl): JavaBidirectionalStreamingRequestBuilder[I, O] =
    new JavaBidirectionalStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)
}

/**
 * INTERNAL API
 */
@InternalApi
trait MetadataOperations[T <: MetadataOperations[T]] {
  def headers: MetadataImpl
  def withHeaders(headers: MetadataImpl): T

  def addHeader(key: String, value: String): T =
    withHeaders(headers = headers.addEntry(key, value))

  def addHeader(key: String, value: ByteString): T =
    withHeaders(headers = headers.addEntry(key, value))
}
