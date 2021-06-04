/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.NotUsed
import akka.actor.ActorSystem

import akka.grpc.internal.Identity
import akka.grpc.internal.GrpcRequestHelpers
import akka.grpc.internal.GrpcProtocolNative

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

import org.openjdk.jmh.annotations._

import grpc.reflection.v1alpha.reflection._

class HandlerProcessingBenchmark extends CommonBenchmark {
  implicit val system = ActorSystem("bench")
  implicit val writer = GrpcProtocolNative.newWriter(Identity)

  val in = Source.repeat(ServerReflectionRequest()).take(10000)
  val request: HttpRequest = {
    implicit val serializer = ServerReflection.Serializers.ServerReflectionRequestSerializer
    GrpcRequestHelpers(Uri("https://unused.example/" + ServerReflection.name + "/ServerReflectionInfo"), Nil, in)
  }

  val handler: HttpRequest => Future[HttpResponse] = ServerReflectionHandler(new ServerReflection {
    override def serverReflectionInfo(
        in: Source[ServerReflectionRequest, NotUsed]): Source[ServerReflectionResponse, NotUsed] =
      in.map(_ => ServerReflectionResponse())
  })

  @Benchmark
  @OperationsPerInvocation(10000)
  def streamingRequestProcessing(): Unit = {
    val response = Await.result(handler(request), Duration.Inf)
    // Blackhole the response
    Await.result(response.entity.dataBytes.runWith(Sink.ignore), Duration.Inf)
    assert(response.status == StatusCodes.OK)
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
