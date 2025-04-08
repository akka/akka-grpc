/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.time.{ Duration => JDuration }
import java.util.concurrent.{ CompletionStage, TimeUnit }
import akka.NotUsed
import akka.actor.ClassicActorSystemProvider
import akka.annotation.{ InternalApi, InternalStableApi }
import akka.grpc.javadsl
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcServiceException, GrpcSingleResponse }
import akka.pattern.RetrySettings
import akka.stream.{ Graph, Materializer, SourceShape }
import akka.stream.javadsl.{ Source => JavaSource }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import io.grpc._

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.FutureConverters._

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaUnaryRequestBuilder[I, O](
    descriptor: MethodDescriptor[I, O],
    channel: InternalChannel,
    defaultOptions: CallOptions,
    settings: GrpcClientSettings,
    val headers: MetadataImpl,
    retrySettings: Option[RetrySettings])(implicit ec: ExecutionContext, system: ClassicActorSystemProvider)
    extends akka.grpc.scaladsl.SingleResponseRequestBuilder[I, O]
    with MetadataOperations[ScalaUnaryRequestBuilder[I, O]] {

  // for backwards compatibility with clients generated with 2.5.3 and earlier
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings,
      headers: MetadataImpl)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, headers, None)(ec, null)

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)(ec, null)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  private def systemOrThrow() =
    if (system eq null)
      throw new IllegalStateException(
        "Retry settings cannot be used with clients generated with Akka 2.5.3 and earlier")
    else system

  override def invoke(request: I): Future[O] = {
    def callIt() =
      channel.invoke(request, headers, descriptor, defaultOptions).recoverWith(RequestBuilderImpl.richError)
    retrySettings match {
      case None => callIt()
      case Some(settings) =>
        akka.pattern.retry(settings)(callIt _)(systemOrThrow())
    }
  }

  override def invokeWithMetadata(request: I): Future[GrpcSingleResponse[O]] = {
    def callIt() =
      channel
        .invokeWithMetadata(request, headers, descriptor, callOptionsWithDeadline())
        .recoverWith(RequestBuilderImpl.richError)
    retrySettings match {
      case None => callIt()
      case Some(settings) =>
        akka.pattern.retry(settings)(callIt _)(systemOrThrow())
    }
  }

  override def withHeaders(headers: MetadataImpl): ScalaUnaryRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: Duration): SingleResponseRequestBuilder[I, O] =
    copy(defaultOptions =
      if (!deadline.isFinite) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  override def withRetry(retrySettings: RetrySettings): SingleResponseRequestBuilder[I, O] =
    copy(retrySettings = Some(retrySettings))

  override def withRetry(maxRetries: Int): SingleResponseRequestBuilder[I, O] = withRetry(RetrySettings(maxRetries))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers,
      retrySettings: Option[RetrySettings] = retrySettings): ScalaUnaryRequestBuilder[I, O] =
    new ScalaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)
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
    val headers: MetadataImpl,
    retrySettings: Option[RetrySettings])(implicit ex: ExecutionContext, system: ClassicActorSystemProvider)
    extends akka.grpc.javadsl.SingleResponseRequestBuilder[I, O]
    with MetadataOperations[JavaUnaryRequestBuilder[I, O]] {
  private def delegate =
    new ScalaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)

  // for backwards compatibility with clients generated with 2.5.3 and earlier
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings,
      headers: MetadataImpl)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, headers, None)(ec, null)

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)(ec, null)

  override def invoke(request: I): CompletionStage[O] =
    delegate.invoke(request).asJava

  override def invokeWithMetadata(request: I): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request).asJava

  override def withHeaders(headers: MetadataImpl): JavaUnaryRequestBuilder[I, O] =
    new JavaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)

  override def setDeadline(deadline: JDuration): JavaUnaryRequestBuilder[I, O] =
    copy(defaultOptions =
      if (deadline == null) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  override def withRetry(retrySettings: RetrySettings): javadsl.SingleResponseRequestBuilder[I, O] =
    copy(retrySettings = Some(retrySettings))

  override def withRetry(maxRetries: Int): javadsl.SingleResponseRequestBuilder[I, O] = withRetry(
    RetrySettings(maxRetries))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers,
      retrySettings: Option[RetrySettings] = retrySettings): JavaUnaryRequestBuilder[I, O] =
    new JavaUnaryRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)
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
    val headers: MetadataImpl,
    retrySettings: Option[RetrySettings])(implicit mat: Materializer, ec: ExecutionContext)
    extends akka.grpc.scaladsl.SingleResponseRequestBuilder[Source[I, NotUsed], O]
    with MetadataOperations[ScalaClientStreamingRequestBuilder[I, O]] {

  // this is what the generated client scala code uses
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings,
      headers: MetadataImpl)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, headers, None)

  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: Source[I, NotUsed]): Future[O] =
    invokeWithMetadata(request).map(_.value)(ExecutionContext.parasitic).recoverWith(RequestBuilderImpl.richError)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Future[GrpcSingleResponse[O]] = {
    def invokeIt(): Future[GrpcSingleResponse[O]] = {
      // a bit much overhead here because we are using the flow to represent a single response
      val src =
        channel.invokeWithMetadata(source, headers, descriptor, false, callOptionsWithDeadline())
      val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
        src
          // Continue reading to get the trailing headers
          .via(new CancellationBarrierGraphStage)
          .toMat(Sink.head)(Keep.both)
          .run()

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
              def getTrailers() = metadata.getTrailers()
            }
        }(ExecutionContext.parasitic)
        .recoverWith(RequestBuilderImpl.richError)
    }

    retrySettings match {
      case Some(settings) =>
        akka.pattern.retry(settings)(invokeIt _)(mat.system)
      case None =>
        invokeIt()
    }
  }

  override def withHeaders(headers: MetadataImpl): ScalaClientStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: Duration): ScalaClientStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (!deadline.isFinite) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  override def withRetry(retrySettings: RetrySettings): SingleResponseRequestBuilder[Source[I, NotUsed], O] =
    copy(retrySettings = Some(retrySettings))

  override def withRetry(maxRetries: Int): SingleResponseRequestBuilder[Source[I, NotUsed], O] = withRetry(
    RetrySettings(maxRetries))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers,
      retrySettings: Option[RetrySettings] = retrySettings): ScalaClientStreamingRequestBuilder[I, O] =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)
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
    val headers: MetadataImpl,
    retrySettings: Option[RetrySettings])(implicit mat: Materializer, ec: ExecutionContext)
    extends akka.grpc.javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O]
    with MetadataOperations[JavaClientStreamingRequestBuilder[I, O]] {
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)

  @deprecated("fqMethodName was removed since it can be derived from the descriptor", "1.1.0")
  @InternalStableApi
  def this(
      descriptor: MethodDescriptor[I, O],
      fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty, None)

  // used by the generated client code
  def this(
      descriptor: MethodDescriptor[I, O],
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings,
      headers: MetadataImpl)(implicit mat: Materializer, ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, headers, None)

  private def delegate =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)

  override def invoke(request: JavaSource[I, NotUsed]): CompletionStage[O] =
    delegate.invoke(request.asScala).asJava

  override def invokeWithMetadata(request: JavaSource[I, NotUsed]): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request.asScala).asJava

  override def withHeaders(headers: MetadataImpl): JavaClientStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: JDuration): JavaClientStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (deadline == null) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  override def withRetry(
      retrySettings: RetrySettings): javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O] =
    copy(retrySettings = Some(retrySettings))

  override def withRetry(maxRetries: Int): javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O] =
    withRetry(RetrySettings(maxRetries))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers,
      retrySettings: Option[RetrySettings] = retrySettings): JavaClientStreamingRequestBuilder[I, O] =
    new JavaClientStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers, retrySettings)
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
      fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: I): Source[O, NotUsed] =
    invokeWithMetadata(request)
      .mapMaterializedValue(_ => NotUsed)
      .recoverWithRetries(1, RequestBuilderImpl.richErrorStream)

  override def invokeWithMetadata(request: I): Source[O, Future[GrpcResponseMetadata]] =
    channel
      .invokeWithMetadata(Source.single(request), headers, descriptor, true, callOptionsWithDeadline())
      .recoverWithRetries(1, RequestBuilderImpl.richErrorStream)

  override def withHeaders(headers: MetadataImpl): ScalaServerStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: Duration): ScalaServerStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (!deadline.isFinite) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers): ScalaServerStreamingRequestBuilder[I, O] =
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
      fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private val delegate =
    new ScalaServerStreamingRequestBuilder[I, O](descriptor, channel, defaultOptions, settings, headers)

  override def invoke(request: I): JavaSource[O, NotUsed] =
    delegate.invoke(request).asJava

  override def invokeWithMetadata(source: I): JavaSource[O, CompletionStage[GrpcResponseMetadata]] =
    delegate.invokeWithMetadata(source).mapMaterializedValue(_.asJava).asJava

  override def withHeaders(headers: MetadataImpl): JavaServerStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: JDuration): JavaServerStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (deadline == null) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers): JavaServerStreamingRequestBuilder[I, O] =
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
      fqMethodName: String,
      channel: InternalChannel,
      defaultOptions: CallOptions,
      settings: GrpcClientSettings)(implicit ec: ExecutionContext) =
    this(descriptor, channel, defaultOptions, settings, MetadataImpl.empty)

  private def callOptionsWithDeadline(): CallOptions =
    NettyClientUtils.callOptionsWithDeadline(defaultOptions, settings)

  override def invoke(request: Source[I, NotUsed]): Source[O, NotUsed] =
    invokeWithMetadata(request)
      .mapMaterializedValue(_ => NotUsed)
      .recoverWithRetries(1, RequestBuilderImpl.richErrorStream)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Source[O, Future[GrpcResponseMetadata]] =
    channel
      .invokeWithMetadata(source, headers, descriptor, true, callOptionsWithDeadline())
      .recoverWithRetries(1, RequestBuilderImpl.richErrorStream)

  override def withHeaders(headers: MetadataImpl): ScalaBidirectionalStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: Duration): ScalaBidirectionalStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (!deadline.isFinite) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers): ScalaBidirectionalStreamingRequestBuilder[I, O] =
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
      fqMethodName: String,
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
    delegate.invokeWithMetadata(source.asScala).mapMaterializedValue(_.asJava).asJava

  override def withHeaders(headers: MetadataImpl): JavaBidirectionalStreamingRequestBuilder[I, O] =
    copy(headers = headers)

  override def setDeadline(deadline: JDuration): JavaBidirectionalStreamingRequestBuilder[I, O] =
    copy(defaultOptions =
      if (deadline == null) defaultOptions.withDeadline(null)
      else defaultOptions.withDeadlineAfter(deadline.toMillis, TimeUnit.MILLISECONDS))

  private def copy(
      defaultOptions: CallOptions = defaultOptions,
      headers: MetadataImpl = headers): JavaBidirectionalStreamingRequestBuilder[I, O] =
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

object RequestBuilderImpl {
  def richErrorStream[U]: PartialFunction[Throwable, Graph[SourceShape[U], NotUsed]] = {
    case item => Source.failed(RequestBuilderImpl.lift(item))
  }

  def richError[U]: PartialFunction[Throwable, Future[U]] = {
    case item => Future.failed(RequestBuilderImpl.lift(item))
  }

  def lift(item: Throwable): scala.Throwable = item match {
    case ex: StatusRuntimeException => GrpcServiceException(ex)
    case other                      => other
  }

}
