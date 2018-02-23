package com.lightbend.grpc.interop

import akka.NotUsed
import akka.http.grpc._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.google.protobuf.empty.Empty
import com.google.protobuf.{ ByteString, EmptyProtos }
import io.grpc.testing.integration.Messages
import io.grpc.testing.integration.Messages.Payload

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

// TODO this trait would be generated from the proto file at https://github.com/grpc/grpc-java/blob/master/interop-testing/src/main/proto/io/grpc/testing/integration/test.proto
// and move to the 'server' project
trait TestService {
  def emptyCall(req: EmptyProtos.Empty): Future[EmptyProtos.Empty]
  def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse]
  def cacheableUnaryCall(in: Messages.SimpleRequest): Future[Messages.SimpleResponse]
  def fullDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, Any]
  def halfDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, Any]
  def streamingInputCall(in: Source[Messages.StreamingInputCallRequest, _]): Future[Messages.StreamingInputCallResponse]
  def streamingOutputCall(in: Messages.StreamingOutputCallRequest): Source[Messages.StreamingOutputCallResponse, Any]
  def unimplementedCall(in: Empty): Future[Empty]
}

// TODO this descriptor would be generated from the proto file at https://github.com/grpc/grpc-java/blob/master/interop-testing/src/main/proto/io/grpc/testing/integration/test.proto
// and move to the 'server' project
object TestService {
  import GoogleProtobufSerializer._
  val descriptor = {
    val builder = new ServerInvokerBuilder[TestService]
    Descriptor[TestService]("grpc.testing.TestService", Seq(
      CallDescriptor.named("EmptyCall", builder.unaryToUnary(_.emptyCall)),
      CallDescriptor.named("UnaryCall", builder.unaryToUnary(_.unaryCall)),
      CallDescriptor.named("StreamingInputCall", builder.streamToUnary(_.streamingInputCall))))
  }
}

// TODO implement this from io.grpc.testing.integration.TestServiceImpl
// and move to the 'server' project
class TestServiceImpl(implicit ec: ExecutionContext, mat: Materializer) extends TestService {
  override def emptyCall(req: EmptyProtos.Empty) = Future.successful(EmptyProtos.Empty.getDefaultInstance)
  override def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse] = Future {
    Messages.SimpleResponse.newBuilder
      .setPayload(
        Payload.newBuilder()
          .setBody(ByteString.copyFrom(new Array[Byte](req.getResponseSize))))
      .build()
  }
  override def cacheableUnaryCall(in: Messages.SimpleRequest): Future[Messages.SimpleResponse] = ???
  override def fullDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, Any] = ???
  override def halfDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, Any] = ???

  override def streamingInputCall(in: Source[Messages.StreamingInputCallRequest, _]): Future[Messages.StreamingInputCallResponse] = {
    in
      .map(_.getPayload.getBody.size)
      .runFold(0)(_ + _)
      .map { sum =>
        Messages.StreamingInputCallResponse.newBuilder()
          .setAggregatedPayloadSize(sum)
          .build()
      }
  }

  override def streamingOutputCall(in: Messages.StreamingOutputCallRequest): Source[Messages.StreamingOutputCallResponse, Any] = ???
  override def unimplementedCall(in: Empty): Future[Empty] = ???
}

// TODO a serializer should be generated from the .proto files
object GoogleProtobufSerializer {
  implicit def googlePbSerializer[T <: com.google.protobuf.Message: ClassTag]: ProtobufSerializer[T] = {
    new GoogleProtobufSerializer(implicitly[ClassTag[T]])
  }
}

// TODO a serializer should be generated from the .proto files
class GoogleProtobufSerializer[T <: com.google.protobuf.Message](classTag: ClassTag[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T) = akka.util.ByteString(t.toByteArray)
  override def deserialize(bytes: akka.util.ByteString): T = {
    val parser = classTag.runtimeClass.getMethod("parseFrom", classOf[Array[Byte]])
    parser.invoke(classTag.runtimeClass, bytes.toArray).asInstanceOf[T]
  }
}
