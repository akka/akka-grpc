package com.lightbend.grpc.interop

import akka.util.ByteString
import com.google.protobuf.EmptyProtos
import io.grpc.stub.StreamObserver
import io.grpc.testing.integration.Messages
import io.grpc.testing.integration.{ TestServiceImpl => GoogleTestServiceImpl }
import akka.http.grpc._

import scala.concurrent.{ Future, Promise }
import scala.reflect.ClassTag

trait TestService {
  def emptyCall(req: EmptyProtos.Empty): Future[EmptyProtos.Empty]
  def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse]
}

class TestServiceImpl(googleImpl: GoogleTestServiceImpl) extends TestService {
  override def emptyCall(req: EmptyProtos.Empty) = Future.successful(EmptyProtos.Empty.getDefaultInstance)
  override def unaryCall(req: Messages.SimpleRequest): Future[Messages.SimpleResponse] = {
    val promise = Promise[Messages.SimpleResponse]
    googleImpl.unaryCall(req, new StreamObserver[Messages.SimpleResponse] {
      override def onError(t: Throwable): Unit = promise.failure(t)
      override def onCompleted(): Unit = ()
      override def onNext(value: Messages.SimpleResponse): Unit = promise.success(value)
    })
    promise.future
  }
}

object TestService {
  import GoogleProtobufSerializer._
  val descriptor = {
    val builder = new ServerInvokerBuilder[TestService]
    Descriptor[TestService]("grpc.testing.TestService", Seq(
      CallDescriptor.named("EmptyCall", builder.unaryToUnary(_.emptyCall)),
      CallDescriptor.named("UnaryCall", builder.unaryToUnary(_.unaryCall)),
      CallDescriptor.named("CacheableUnaryCall", builder.unaryToUnary(_.unaryCall))))
  }
}

object GoogleProtobufSerializer {
  implicit def googlePbSerializer[T <: com.google.protobuf.Message: ClassTag]: ProtobufSerializer[T] = {
    new GoogleProtobufSerializer(implicitly[ClassTag[T]])
  }
}

class GoogleProtobufSerializer[T <: com.google.protobuf.Message](classTag: ClassTag[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T) = ByteString(t.toByteArray)
  override def deserialize(bytes: ByteString): T = {
    val parser = classTag.runtimeClass.getMethod("parseFrom", classOf[Array[Byte]])
    parser.invoke(classTag.runtimeClass, bytes.toArray).asInstanceOf[T]
  }
}
