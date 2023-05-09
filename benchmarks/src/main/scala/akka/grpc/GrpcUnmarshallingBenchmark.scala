/*
 * Copyright (C) 2021-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.actor.ActorSystem
import akka.grpc.internal.{ GrpcProtocolNative, GrpcRequestHelpers, Identity }
import akka.grpc.scaladsl.{ GrpcMarshalling, ScalapbProtobufSerializer }
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import grpc.reflection.v1alpha.reflection._
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class GrpcUnmarshallingBenchmark extends CommonBenchmark {
  implicit val system: ActorSystem = ActorSystem("bench")
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)
  implicit val reader: GrpcProtocol.GrpcProtocolReader = GrpcProtocolNative.newReader(Identity)
  implicit val serializer: ScalapbProtobufSerializer[ServerReflectionRequest] =
    ServerReflection.Serializers.ServerReflectionRequestSerializer

  val request = GrpcRequestHelpers(
    Uri("https://unused.example/" + ServerReflection.name + "/ServerReflectionInfo"),
    Nil,
    Source.single(ServerReflectionRequest()))

  @Benchmark
  def unmarshall(): ServerReflectionRequest = {
    Await.result(GrpcMarshalling.unmarshal(request), 3.seconds)
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
