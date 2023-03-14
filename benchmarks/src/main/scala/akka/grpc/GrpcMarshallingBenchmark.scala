/*
 * Copyright (C) 2021-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ActorSystem
import akka.grpc.internal.{ GrpcProtocolNative, Identity }
import akka.grpc.scaladsl.GrpcMarshalling
import akka.grpc.scaladsl.ScalapbProtobufSerializer
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Source
import grpc.reflection.v1alpha.reflection._
import org.openjdk.jmh.annotations._

// Microbenchmarks for GrpcMarshalling.
// Does not actually benchmarks the actual marshalling because we dont consume the HttpResponse
class GrpcMarshallingBenchmark extends CommonBenchmark {
  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val reader: GrpcProtocol.GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  implicit val serializer: ScalapbProtobufSerializer[ServerReflectionRequest] =
    ServerReflection.Serializers.ServerReflectionRequestSerializer

  @Benchmark
  def marshall(): HttpResponse = {
    GrpcMarshalling.marshal(ServerReflectionRequest())
  }

  @Benchmark
  def marshallStream(): HttpResponse = {
    GrpcMarshalling.marshalStream(Source.repeat(ServerReflectionRequest()).take(10000))
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
