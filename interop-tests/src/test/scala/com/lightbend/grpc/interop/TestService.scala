package com.lightbend.grpc.interop

import akka.http.grpc._
import akka.stream.{ Materializer, javadsl }
import akka.stream.scaladsl.{ Flow, Source }
import com.google.protobuf.EmptyProtos.Empty
import com.google.protobuf.{ ByteString, EmptyProtos }
import io.grpc.testing.integration.Messages
import io.grpc.testing.integration.Messages.{ Payload }
import io.grpc.testing.integration.test.TestServiceService

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

object TestServiceImpl {
  val parametersToResponseFlow: Flow[Messages.ResponseParameters, Messages.StreamingOutputCallResponse, _] =
    Flow[Messages.ResponseParameters]
      .map { parameters =>
        Messages.StreamingOutputCallResponse.newBuilder()
          .setPayload(Payload.newBuilder()
            .setBody(ByteString.copyFrom(new Array[Byte](parameters.getSize))))
          .build
      }
}

class TestServiceImpl(implicit ec: ExecutionContext, mat: Materializer) extends TestServiceService {
  import TestServiceImpl._

  override def emptyCall(req: EmptyProtos.Empty) = Future.successful(EmptyProtos.Empty.getDefaultInstance)
  override def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse] = Future {
    Messages.SimpleResponse.newBuilder
      .setPayload(
        Payload.newBuilder()
          .setBody(ByteString.copyFrom(new Array[Byte](req.getResponseSize))))
      .build()
  }
  override def cacheableUnaryCall(in: Messages.SimpleRequest): Future[Messages.SimpleResponse] = ???

  override def fullDuplexCall(in: Source[Messages.StreamingOutputCallRequest, _]): Source[Messages.StreamingOutputCallResponse, Any] =
    in.asJava.mapConcat(_.getResponseParametersList).asScala.via(parametersToResponseFlow)

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

  override def streamingOutputCall(in: Messages.StreamingOutputCallRequest): Source[Messages.StreamingOutputCallResponse, Any] =
    javadsl.Source.from(in.getResponseParametersList).asScala.via(parametersToResponseFlow)

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
