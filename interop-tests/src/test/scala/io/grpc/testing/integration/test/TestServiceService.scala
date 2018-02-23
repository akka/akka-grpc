package io.grpc.testing.integration.test

import scala.concurrent.Future

import akka.stream.scaladsl.Source

trait TestServiceService {

  def emptyCall(in: _root_.com.google.protobuf.EmptyProtos.Empty): Future[_root_.com.google.protobuf.EmptyProtos.Empty]

  def unaryCall(in: _root_.io.grpc.testing.integration.Messages.SimpleRequest): Future[_root_.io.grpc.testing.integration.Messages.SimpleResponse]

  def cacheableUnaryCall(in: _root_.io.grpc.testing.integration.Messages.SimpleRequest): Future[_root_.io.grpc.testing.integration.Messages.SimpleResponse]

  def streamingOutputCall(in: _root_.io.grpc.testing.integration.Messages.StreamingOutputCallRequest): Source[_root_.io.grpc.testing.integration.Messages.StreamingOutputCallResponse, Any]

  def streamingInputCall(in: Source[_root_.io.grpc.testing.integration.Messages.StreamingInputCallRequest, _]): Future[_root_.io.grpc.testing.integration.Messages.StreamingInputCallResponse]

  def fullDuplexCall(in: Source[_root_.io.grpc.testing.integration.Messages.StreamingOutputCallRequest, _]): Source[_root_.io.grpc.testing.integration.Messages.StreamingOutputCallResponse, Any]

  def halfDuplexCall(in: Source[_root_.io.grpc.testing.integration.Messages.StreamingOutputCallRequest, _]): Source[_root_.io.grpc.testing.integration.Messages.StreamingOutputCallResponse, Any]

  def unimplementedCall(in: _root_.com.google.protobuf.EmptyProtos.Empty): Future[_root_.com.google.protobuf.EmptyProtos.Empty]

}
object TestServiceService {
  val name = "grpc.testing.TestService"
}
