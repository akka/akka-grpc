package akka.grpc.internal

import akka.NotUsed
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.grpc.GrpcSingleResponse
import akka.grpc.javadsl.SingleBlockingResponseRequestBuilder
import akka.stream.javadsl.{ Source => JavaSource }

import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

/**
 * INTERNAL PAI
 */
@InternalApi
private[akka] object BlockingRequestBuilder {

  def unwrapExecutionException(exc: ExecutionException): RuntimeException = {
    exc.getCause match {
      case null                => new RuntimeException(exc.getMessage)
      case e: RuntimeException => e
      case other               => new RuntimeException(other)
    }
  }

}

/**
 * INTERNAL API
 */
// instantiated by generated code, constructor must be kept binary compatible
@InternalStableApi
private[akka] final class JavaSingleBlockingResponseRequestBuilder[I, O](delegate: JavaUnaryRequestBuilder[I, O])
    extends akka.grpc.javadsl.SingleBlockingResponseRequestBuilder[I, O]
    with MetadataOperations[JavaSingleBlockingResponseRequestBuilder[I, O]] {

  override def invoke(request: I): O =
    try {
      delegate.invoke(request).toCompletableFuture.get() // timeout handled by client
    } catch {
      case ex: ExecutionException => throw BlockingRequestBuilder.unwrapExecutionException(ex)
    }

  override def invokeAsync(request: I): CompletionStage[O] =
    delegate.invoke(request)

  override def invokeWithMetadata(request: I): GrpcSingleResponse[O] =
    try {
      delegate.invokeWithMetadata(request).toCompletableFuture.get() // timeout handled by client
    } catch {
      case ex: ExecutionException => throw BlockingRequestBuilder.unwrapExecutionException(ex)
    }

  override def invokeWithMetadataAsync(request: I): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request)

  override def setDeadline(deadline: Duration): SingleBlockingResponseRequestBuilder[I, O] =
    copy(delegate.setDeadline(deadline))

  override def headers: MetadataImpl = delegate.headers

  override def withHeaders(headers: MetadataImpl): JavaSingleBlockingResponseRequestBuilder[I, O] = copy(
    delegate.withHeaders(headers))

  private def copy(delegate: JavaUnaryRequestBuilder[I, O]): JavaSingleBlockingResponseRequestBuilder[I, O] =
    new JavaSingleBlockingResponseRequestBuilder[I, O](delegate)
}

/**
 * INTERNAL API
 */
@InternalStableApi
// instantiated by generated code, constructor must be kept binary compatible
private[akka] final class JavaClientStreamingBlockingResponseRequestBuilder[I, O](
    delegate: JavaClientStreamingRequestBuilder[I, O])
    extends akka.grpc.javadsl.SingleBlockingResponseRequestBuilder[JavaSource[I, NotUsed], O]
    with MetadataOperations[JavaClientStreamingBlockingResponseRequestBuilder[I, O]] {
  override def invoke(request: JavaSource[I, NotUsed]): O =
    try {
      delegate.invoke(request).toCompletableFuture.get() // timeout handled by client
    } catch {
      case ex: ExecutionException => throw BlockingRequestBuilder.unwrapExecutionException(ex)
    }

  override def invokeAsync(request: JavaSource[I, NotUsed]): CompletionStage[O] =
    delegate.invoke(request)

  override def invokeWithMetadata(request: JavaSource[I, NotUsed]): GrpcSingleResponse[O] =
    try {
      delegate.invokeWithMetadata(request).toCompletableFuture.get() // timeout handled by client
    } catch {
      case ex: ExecutionException => throw BlockingRequestBuilder.unwrapExecutionException(ex)
    }

  override def invokeWithMetadataAsync(request: JavaSource[I, NotUsed]): CompletionStage[GrpcSingleResponse[O]] =
    delegate.invokeWithMetadata(request)

  override def setDeadline(deadline: Duration): SingleBlockingResponseRequestBuilder[JavaSource[I, NotUsed], O] =
    copy(delegate.setDeadline(deadline))

  override def headers: MetadataImpl = delegate.headers

  override def withHeaders(headers: MetadataImpl): JavaClientStreamingBlockingResponseRequestBuilder[I, O] =
    copy(delegate.withHeaders(headers))

  private def copy(
      delegate: JavaClientStreamingRequestBuilder[I, O]): JavaClientStreamingBlockingResponseRequestBuilder[I, O] =
    new JavaClientStreamingBlockingResponseRequestBuilder[I, O](delegate)
}
