/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.grpc.internal.reflection_test_1.ReflectionTest1Proto
import akka.grpc.internal.reflection_test_2.ReflectionTest2Proto
import akka.grpc.internal.reflection_test_3.ReflectionTest3Proto
import akka.grpc.internal.reflection_test_4.ReflectionTest4Proto
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestKit
import com.google.protobuf.descriptor.FileDescriptorProto
import grpc.reflection.v1alpha.reflection.ServerReflectionRequest.MessageRequest
import grpc.reflection.v1alpha.reflection.{ ServerReflection, ServerReflectionRequest, ServerReflectionResponse }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ServerReflectionImplSpec
    extends TestKit(ActorSystem("ServerReflectionImplSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with OptionValues {
  import ServerReflectionImpl._
  "The Server Reflection implementation utilities" should {
    "split strings up until the next dot" in {
      splitNext("foo.bar") should be(("foo", "bar"))
      splitNext("foo.bar.baz") should be(("foo", "bar.baz"))
    }
    "find a symbol" in {
      containsSymbol("grpc.reflection.v1alpha.ServerReflection", ServerReflection.descriptor) should be(true)
      containsSymbol("grpc.reflection.v1alpha.Foo", ServerReflection.descriptor) should be(false)
      containsSymbol("foo.Foo", ServerReflection.descriptor) should be(false)
    }
  }

  "The Server Reflection implementation" should {
    val serverReflection =
      ServerReflectionImpl(
        Seq(
          ServerReflection.descriptor,
          ReflectionTest1Proto.javaDescriptor,
          ReflectionTest2Proto.javaDescriptor,
          ReflectionTest3Proto.javaDescriptor,
          ReflectionTest4Proto.javaDescriptor),
        List.empty[String])

    "retrieve server reflection info" in {
      val serverReflectionRequest = ServerReflectionRequest(messageRequest =
        MessageRequest.FileByFilename("grpc/reflection/v1alpha/reflection.proto"))

      val serverReflectionResponse =
        serverReflection.serverReflectionInfo(Source.single(serverReflectionRequest)).runWith(Sink.head).futureValue

      serverReflectionResponse.messageResponse.listServicesResponse should be(empty)

      serverReflectionResponse.messageResponse.fileDescriptorResponse.value.fileDescriptorProto
        .map(_.size()) should contain only ServerReflection.descriptor.toProto.toByteString.size()
    }

    "not retrieve reflection info for an unknown proto file name" in {
      val serverReflectionRequest =
        ServerReflectionRequest(messageRequest = MessageRequest.FileByFilename("grpc/reflection/v1alpha/unknown.proto"))

      val serverReflectionResponse =
        serverReflection.serverReflectionInfo(Source.single(serverReflectionRequest)).runWith(Sink.head).futureValue

      serverReflectionResponse.messageResponse.listServicesResponse should be(empty)
      serverReflectionResponse.messageResponse.fileDescriptorResponse.value.fileDescriptorProto should be(empty)
    }

    "return transitive dependencies" in {
      val serverReflectionRequest = ServerReflectionRequest(messageRequest =
        MessageRequest.FileByFilename("akka/grpc/internal/reflection_test_1.proto"))

      val serverReflectionResponse =
        serverReflection.serverReflectionInfo(Source.single(serverReflectionRequest)).runWith(Sink.head).futureValue

      val protos = decodeFileResponseToNames(serverReflectionResponse)
      protos should have size 4
      (protos should contain).allOf(
        "akka/grpc/internal/reflection_test_1.proto",
        "akka/grpc/internal/reflection_test_2.proto",
        "akka/grpc/internal/reflection_test_3.proto",
        "akka/grpc/internal/reflection_test_4.proto")
    }

    "not return transitive dependencies already sent" in {
      val req1 = ServerReflectionRequest(messageRequest =
        MessageRequest.FileByFilename("akka/grpc/internal/reflection_test_4.proto"))
      val req2 = ServerReflectionRequest(messageRequest =
        MessageRequest.FileByFilename("akka/grpc/internal/reflection_test_1.proto"))
      val req3 = ServerReflectionRequest(messageRequest =
        MessageRequest.FileByFilename("akka/grpc/internal/reflection_test_2.proto"))

      val responses =
        serverReflection.serverReflectionInfo(Source(List(req1, req2, req3))).runWith(Sink.seq).futureValue

      (responses should have).length(3)

      val protos1 = decodeFileResponseToNames(responses.head)
      protos1 should have size 1
      protos1.head shouldBe "akka/grpc/internal/reflection_test_4.proto"

      val protos2 = decodeFileResponseToNames(responses(1))
      // all except 4, because 4 has already been sent
      protos2 should have size 3
      (protos2 should contain).allOf(
        "akka/grpc/internal/reflection_test_1.proto",
        "akka/grpc/internal/reflection_test_2.proto",
        "akka/grpc/internal/reflection_test_3.proto")

      val protos3 = decodeFileResponseToNames(responses(2))
      // should still include 2, because 2 was explicitly requested, but should not include anything else
      // because everything has already been sent
      protos3 should have size 1
      protos3.head shouldBe "akka/grpc/internal/reflection_test_2.proto"

    }

  }

  private def decodeFileResponseToNames(response: ServerReflectionResponse): Seq[String] =
    response.messageResponse.fileDescriptorResponse.value.fileDescriptorProto.map(bs =>
      FileDescriptorProto.parseFrom(bs.newCodedInput()).name.getOrElse(""))

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
