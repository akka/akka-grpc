package io.grpc.testing.integration.test

import _root_.akka.stream.Materializer
import _root_.akka.stream.scaladsl.Source
import _root_.com.trueaccord.scalapb.grpc.{ConcreteProtoFileDescriptorSupplier, Grpc}
import _root_.io.grpc.stub.{ClientCalls, ServerCalls, StreamObserver}
import _root_.io.grpc.{CallOptions, Channel, MethodDescriptor, ServerServiceDefinition}
import akka.http.grpc.GrpcMarshalling.Marshaller
import com.google.protobuf.empty.Empty
import io.grpc.testing.integration.messages._

import _root_.scala.concurrent.Future
import _root_.scala.util.{Failure, Success}

/**
 * Hard-coded client stub to help us define what needs to be the final generated stub.
 */
object TestServiceAkkaGrpc {


  def stub(channel: Channel): TestServiceStub = new TestServiceStub(channel)

  class TestServiceStub(channel: Channel, options: CallOptions = CallOptions.DEFAULT) extends TestServiceService {

    override def emptyCall(in: Empty): Future[Empty] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_EMPTY_CALL, options), in))

    override def unaryCall(in: SimpleRequest): Future[SimpleResponse] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_UNARY_CALL, options), in))

    override def cacheableUnaryCall(in: SimpleRequest): Future[SimpleResponse] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_CACHEABLE_UNARY_CALL, options), in))

    override def streamingOutputCall(in: StreamingOutputCallRequest): Source[StreamingOutputCallResponse, _] = ???

    override def streamingInputCall(in: Source[StreamingInputCallRequest, _]): Future[StreamingInputCallResponse] = ???

    override def fullDuplexCall(in: Source[StreamingOutputCallRequest, _]): Source[StreamingOutputCallResponse, _] = ???

    override def halfDuplexCall(in: Source[StreamingOutputCallRequest, _]): Source[StreamingOutputCallResponse, _] = ???

    override def unimplementedCall(in: Empty): Future[Empty] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_UNIMPLEMENTED_CALL, options), in))
  }

  val METHOD_EMPTY_CALL: MethodDescriptor[Empty, Empty] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "EmptyCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.EmptySerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.EmptySerializer))
      .build()

  val METHOD_UNARY_CALL: MethodDescriptor[SimpleRequest, SimpleResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "UnaryCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.SimpleRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.SimpleResponseSerializer))
      .build()

  val METHOD_CACHEABLE_UNARY_CALL: MethodDescriptor[SimpleRequest, SimpleResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "CacheableUnaryCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.SimpleRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.SimpleResponseSerializer))
      .build()

  val METHOD_STREAMING_OUTPUT_CALL: MethodDescriptor[StreamingOutputCallRequest, StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "StreamingOutputCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_STREAMING_INPUT_CALL: MethodDescriptor[StreamingInputCallRequest, StreamingInputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "StreamingInputCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.StreamingInputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.StreamingInputCallResponseSerializer))
      .build()

  val METHOD_FULL_DUPLEX_CALL: MethodDescriptor[StreamingOutputCallRequest, StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "FullDuplexCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_HALF_DUPLEX_CALL: MethodDescriptor[StreamingOutputCallRequest, StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "HalfDuplexCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_UNIMPLEMENTED_CALL: MethodDescriptor[Empty, Empty] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "UnimplementedCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.Serializers.EmptySerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.Serializers.EmptySerializer))
      .build()

  val SERVICE: _root_.io.grpc.ServiceDescriptor = _root_.io.grpc.ServiceDescriptor.newBuilder("grpc.testing.TestService")
    .setSchemaDescriptor(new ConcreteProtoFileDescriptorSupplier(io.grpc.testing.integration.messages.MessagesProto.javaDescriptor))
    .addMethod(METHOD_EMPTY_CALL)
    .addMethod(METHOD_UNARY_CALL)
    .addMethod(METHOD_CACHEABLE_UNARY_CALL)
    .addMethod(METHOD_STREAMING_OUTPUT_CALL)
    .addMethod(METHOD_STREAMING_INPUT_CALL)
    .addMethod(METHOD_FULL_DUPLEX_CALL)
    .addMethod(METHOD_HALF_DUPLEX_CALL)
    .addMethod(METHOD_UNIMPLEMENTED_CALL)
    .build()




}

