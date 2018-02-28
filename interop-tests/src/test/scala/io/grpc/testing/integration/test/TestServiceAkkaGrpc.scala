package io.grpc.testing.integration.test

import _root_.akka.stream.Materializer
import _root_.akka.stream.scaladsl.Source
import _root_.com.trueaccord.scalapb.grpc.{ ConcreteProtoFileDescriptorSupplier, Grpc }
import _root_.io.grpc.stub.{ ClientCalls, ServerCalls, StreamObserver }
import _root_.io.grpc.{ CallOptions, Channel, MethodDescriptor, ServerServiceDefinition }
import akka.http.grpc.GrpcMarshalling.Marshaller
import com.google.protobuf.EmptyProtos
import io.grpc.testing.integration.Messages

import _root_.scala.concurrent.Future
import _root_.scala.util.{ Failure, Success }

/**
 * Hard-coded client stub to help us define what needs to be the final generated stub.
 */
object TestServiceAkkaGrpc {

  val METHOD_EMPTY_CALL: MethodDescriptor[EmptyProtos.Empty, EmptyProtos.Empty] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "EmptyCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.EmptySerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.EmptySerializer))
      .build()

  val METHOD_UNARY_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.SimpleRequest, io.grpc.testing.integration.Messages.SimpleResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "UnaryCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.SimpleRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.SimpleResponseSerializer))
      .build()

  val METHOD_CACHEABLE_UNARY_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.SimpleRequest, io.grpc.testing.integration.Messages.SimpleResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "CacheableUnaryCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.SimpleRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.SimpleResponseSerializer))
      .build()

  val METHOD_STREAMING_OUTPUT_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.StreamingOutputCallRequest, io.grpc.testing.integration.Messages.StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "StreamingOutputCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_STREAMING_INPUT_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.StreamingInputCallRequest, io.grpc.testing.integration.Messages.StreamingInputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "StreamingInputCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.StreamingInputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.StreamingInputCallResponseSerializer))
      .build()

  val METHOD_FULL_DUPLEX_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.StreamingOutputCallRequest, io.grpc.testing.integration.Messages.StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "FullDuplexCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_HALF_DUPLEX_CALL: MethodDescriptor[io.grpc.testing.integration.Messages.StreamingOutputCallRequest, io.grpc.testing.integration.Messages.StreamingOutputCallResponse] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "HalfDuplexCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.StreamingOutputCallRequestSerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.StreamingOutputCallResponseSerializer))
      .build()

  val METHOD_UNIMPLEMENTED_CALL: MethodDescriptor[EmptyProtos.Empty, EmptyProtos.Empty] =
    MethodDescriptor.newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(MethodDescriptor.generateFullMethodName("grpc.testing.TestService", "UnimplementedCall"))
      .setRequestMarshaller(new Marshaller(TestServiceService.EmptySerializer))
      .setResponseMarshaller(new Marshaller(TestServiceService.EmptySerializer))
      .build()

  val SERVICE: _root_.io.grpc.ServiceDescriptor = _root_.io.grpc.ServiceDescriptor.newBuilder("grpc.testing.TestService")
    .setSchemaDescriptor(new ConcreteProtoFileDescriptorSupplier(io.grpc.testing.integration.Messages.getDescriptor))
    .addMethod(METHOD_EMPTY_CALL)
    .addMethod(METHOD_UNARY_CALL)
    .addMethod(METHOD_CACHEABLE_UNARY_CALL)
    .addMethod(METHOD_STREAMING_OUTPUT_CALL)
    .addMethod(METHOD_STREAMING_INPUT_CALL)
    .addMethod(METHOD_FULL_DUPLEX_CALL)
    .addMethod(METHOD_HALF_DUPLEX_CALL)
    .addMethod(METHOD_UNIMPLEMENTED_CALL)
    .build()

  class TestServiceStub(channel: Channel, options: CallOptions = CallOptions.DEFAULT) extends TestServiceService {

    override def emptyCall(in: EmptyProtos.Empty): Future[EmptyProtos.Empty] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_EMPTY_CALL, options), in))

    override def unaryCall(in: Messages.SimpleRequest): Future[Messages.SimpleResponse] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_UNARY_CALL, options), in))

    override def cacheableUnaryCall(in: Messages.SimpleRequest): Future[Messages.SimpleResponse] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_CACHEABLE_UNARY_CALL, options), in))

    override def streamingOutputCall(in: Messages.StreamingOutputCallRequest): Source[Messages.StreamingOutputCallResponse, _] = ???

    override def streamingInputCall(in: Source[Messages.StreamingInputCallRequest, _]): Future[Messages.StreamingInputCallResponse] = ???

    override def fullDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, _] = ???

    override def halfDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, _] = ???

    override def unimplementedCall(in: EmptyProtos.Empty): Future[EmptyProtos.Empty] =
      Grpc.guavaFuture2ScalaFuture(
        ClientCalls.futureUnaryCall(channel.newCall(METHOD_UNIMPLEMENTED_CALL, options), in))
  }

  def bindService(serviceImpl: TestServiceService)(implicit mat: Materializer): ServerServiceDefinition = {

    implicit val exc = mat.executionContext

    ServerServiceDefinition
      .builder(SERVICE)
      .addMethod(
        METHOD_EMPTY_CALL,
        ServerCalls.asyncUnaryCall(
          new ServerCalls.UnaryMethod[EmptyProtos.Empty, EmptyProtos.Empty] {
            override def invoke(in: EmptyProtos.Empty, responseObserver: StreamObserver[EmptyProtos.Empty]) =
              serviceImpl
                .emptyCall(in)
                .onComplete {
                  case Success(value) =>
                    responseObserver.onNext(value)
                    responseObserver.onCompleted()
                  case Failure(t) =>
                    responseObserver.onError(t)
                }
          }))
      .addMethod(
        METHOD_UNARY_CALL,
        ServerCalls.asyncUnaryCall(
          new ServerCalls.UnaryMethod[Messages.SimpleRequest, Messages.SimpleResponse] {
            override def invoke(in: Messages.SimpleRequest, responseObserver: StreamObserver[Messages.SimpleResponse]) =
              serviceImpl
                .unaryCall(in)
                .onComplete {
                  case Success(value) =>
                    responseObserver.onNext(value)
                    responseObserver.onCompleted()
                  case Failure(t) =>
                    responseObserver.onError(t)
                }
          }))
      .addMethod(
        METHOD_CACHEABLE_UNARY_CALL,
        ServerCalls.asyncUnaryCall(
          new ServerCalls.UnaryMethod[Messages.SimpleRequest, Messages.SimpleResponse] {
            override def invoke(in: Messages.SimpleRequest, responseObserver: StreamObserver[Messages.SimpleResponse]) =
              serviceImpl
                .cacheableUnaryCall(in)
                .onComplete {
                  case Success(value) =>
                    responseObserver.onNext(value)
                    responseObserver.onCompleted()
                  case Failure(t) =>
                    responseObserver.onError(t)
                }
          }))
      //      .addMethod(
      //        METHOD_STREAMING_OUTPUT_CALL,
      //        ServerCalls.asyncServerStreamingCall(
      //          new ServerCalls.ServerStreamingMethod[Messages.StreamingOutputCallRequest, Messages.StreamingOutputCallResponse] {
      //            override def invoke(in: Messages.StreamingOutputCallRequest, responseObserver: StreamObserver[Messages.StreamingOutputCallResponse]) =
      //              Source
      //                .single(request)
      //                .via(serviceImpl.streamingOutputCall)
      //                .runForeach(responseObserver.onNext)
      //                .onComplete {
      //                  case Success(_) => responseObserver.onCompleted()
      //                  case Failure(t) => responseObserver.onError(t)
      //                }(mat.executionContext)
      //          }
      //        )
      //      )
      //      .addMethod(
      //        METHOD_STREAMING_INPUT_CALL,
      //        ServerCalls.asyncClientStreamingCall(
      //          new ServerCalls.ClientStreamingMethod[Messages.StreamingInputCallRequest, Messages.StreamingInputCallResponse] {
      //            override def invoke(
      //                                 responseObserver: StreamObserver[Messages.StreamingInputCallResponse]
      //                               ): StreamObserver[Messages.StreamingInputCallRequest] =
      //            // blocks until the GraphStage is fully initialized
      //              Await.result(
      //                Source
      //                  .fromGraph(new GrpcSourceStage[Messages.StreamingInputCallRequest])
      //                  .via(serviceImpl.streamingInputCall)
      //                  .to(Sink.fromSubscriber(grpcObserverToReactiveSubscriber(responseObserver)))
      //                  .run(),
      //                5.seconds
      //              )
      //          }
      //        )
      //      )
      //      .addMethod(
      //        METHOD_FULL_DUPLEX_CALL,
      //        ServerCalls.asyncBidiStreamingCall(
      //          new ServerCalls.BidiStreamingMethod[Messages.StreamingOutputCallRequest, Messages.StreamingOutputCallResponse] {
      //            override def invoke(
      //                                 responseObserver: StreamObserver[Messages.StreamingOutputCallResponse]
      //                               ): StreamObserver[Messages.StreamingOutputCallRequest] =
      //            // blocks until the GraphStage is fully initialized
      //              Await.result(
      //                Source
      //                  .fromGraph(new GrpcSourceStage[Messages.StreamingOutputCallRequest])
      //                  .via(serviceImpl.fullDuplexCall)
      //                  .to(Sink.fromSubscriber(grpcObserverToReactiveSubscriber(responseObserver)))
      //                  .run(),
      //                5.seconds
      //              )
      //          }
      //        )
      //      )
      //      .addMethod(
      //        METHOD_HALF_DUPLEX_CALL,
      //        ServerCalls.asyncBidiStreamingCall(
      //          new ServerCalls.BidiStreamingMethod[Messages.StreamingOutputCallRequest, Messages.StreamingOutputCallResponse] {
      //            override def invoke(
      //                                 responseObserver: StreamObserver[Messages.StreamingOutputCallResponse]
      //                               ): StreamObserver[Messages.StreamingOutputCallRequest] =
      //            // blocks until the GraphStage is fully initialized
      //              Await.result(
      //                Source
      //                  .fromGraph(new GrpcSourceStage[Messages.StreamingOutputCallRequest])
      //                  .via(serviceImpl.halfDuplexCall)
      //                  .to(Sink.fromSubscriber(grpcObserverToReactiveSubscriber(responseObserver)))
      //                  .run(),
      //                5.seconds
      //              )
      //          }
      //        )
      //      )
      //      .addMethod(
      //        METHOD_UNIMPLEMENTED_CALL,
      //        ServerCalls.asyncUnaryCall(
      //          new ServerCalls.UnaryMethod[com.google.protobuf.empty.Empty, com.google.protobuf.empty.Empty] {
      //            override def invoke(request: com.google.protobuf.empty.Empty, responseObserver: StreamObserver[com.google.protobuf.empty.Empty]) =
      //              Source
      //                .single(request)
      //                .via(serviceImpl.unimplementedCall)
      //                .runForeach(responseObserver.onNext)
      //                .onComplete {
      //                  case Success(_) => responseObserver.onCompleted()
      //                  case Failure(t) => responseObserver.onError(t)
      //                }(mat.executionContext)
      //          }
      //        )
      //      )
      .build()
  }

  def stub(channel: Channel): TestServiceStub = new TestServiceStub(channel)

}

