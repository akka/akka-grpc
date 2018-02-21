package com.lightbend.grpc.interop

import com.google.protobuf.ByteString
import com.google.protobuf.EmptyProtos
import io.grpc.testing.integration.Messages
import io.grpc.testing.integration.Messages.Payload

import akka.http.grpc._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

// TODO this trait would be generated from the proto file at https://github.com/grpc/grpc-java/blob/master/interop-testing/src/main/proto/io/grpc/testing/integration/test.proto
// and move to the 'server' project
trait TestService {
  def emptyCall(req: EmptyProtos.Empty): Future[EmptyProtos.Empty]
  def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse]

  def toRoute()(implicit mat: Materializer): Route = {
    import akka.http.scaladsl.server.Directives._
    // TODO would be replaced by scalapb serializer
    import GoogleProtobufSerializer._
    import GrpcRuntimeMarshallingImplicits._

    path("grpc.testing.TestService" / Segment) {
      case "EmptyCall" ⇒
        entity(as[EmptyProtos.Empty]) { req ⇒
          onSuccess(emptyCall(req))(complete(_))
        }
      case "UnaryCall" ⇒
        entity(as[Messages.SimpleRequest]) { req ⇒
          onSuccess(unaryCall(req))(complete(_))
        }
      case _ ⇒ reject()
    }
  }
}

// and move to the 'server' project
class TestServiceImpl(implicit ec: ExecutionContext) extends TestService {
  override def emptyCall(req: EmptyProtos.Empty) = Future.successful(EmptyProtos.Empty.getDefaultInstance)
  override def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse] = Future {
    Messages.SimpleResponse.newBuilder
      .setPayload(
        Payload.newBuilder()
          .setBody(ByteString.copyFrom(new Array[Byte](req.getResponseSize))))
      .build()
  }
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
