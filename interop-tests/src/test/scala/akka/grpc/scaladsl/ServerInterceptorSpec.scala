/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcProtocolNative
import akka.grpc.internal.GrpcRequestHelpers
import akka.grpc.internal.Identity
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.AttributeKey
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration._

class ServerInterceptorSpec
    extends TestKit(ActorSystem("ServerInterceptorSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds)

  implicit val ser: akka.grpc.ProtobufSerializer[HelloRequest] = GreeterService.Serializers.HelloRequestSerializer
  implicit val writer: akka.grpc.GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  val PrincipalKey: AttributeKey[String] = AttributeKey[String]("principal")

  def sayHelloRequest(service: String = GreeterService.name, headers: List[RawHeader] = Nil): HttpRequest =
    GrpcRequestHelpers(s"https://localhost/$service/SayHello", headers, Source.single(HelloRequest("Joe")))

  def grpcStatus(response: HttpResponse): Option[String] =
    response.headers.find(_.name == "grpc-status").map(_.value)

  val requireToken: ServerInterceptor = (_, _, request, next) =>
    if (request.headers.exists(h => h.name == "token" && h.value == "XYZ")) next(request)
    else Future.failed(new GrpcServiceException(Status.UNAUTHENTICATED))

  "The server interceptor" should {

    "reject a call with a gRPC status" in {
      val handler =
        ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), requireToken)

      val rejected = handler(sayHelloRequest()).futureValue
      rejected.status shouldBe StatusCodes.OK
      rejected.entity.contentType shouldBe GrpcProtocolNative.contentType
      grpcStatus(rejected) shouldBe Some(Status.Code.UNAUTHENTICATED.value.toString)

      val accepted = handler(sayHelloRequest(headers = List(RawHeader("token", "XYZ")))).futureValue
      grpcStatus(accepted) shouldBe None
    }

    "pass request attributes to a power api service" in {
      @volatile var seenPrincipal: Option[String] = None
      val impl = new PowerGreeterServiceImpl() {
        override def sayHello(in: HelloRequest, metadata: Metadata): Future[HelloReply] = {
          seenPrincipal = metadata.attribute(PrincipalKey)
          super.sayHello(in, metadata)
        }
      }
      val addPrincipal: ServerInterceptor = (_, _, request, next) => next(request.addAttribute(PrincipalKey, "alice"))
      val handler = ServerInterceptor.intercept(GreeterServicePowerApiHandler.partial(impl), addPrincipal)

      handler(sayHelloRequest()).futureValue
      seenPrincipal shouldBe Some("alice")
    }

    "run interceptors in order and see service and method names" in {
      @volatile var events: List[String] = Nil
      def recording(name: String): ServerInterceptor = (service, method, request, next) => {
        events :+= s"$name-before $service/$method"
        next(request).map { response =>
          events :+= s"$name-after"
          response
        }(system.dispatcher)
      }
      val handler = ServerInterceptor.intercept(
        GreeterServiceHandler.partial(new CountingGreeterServiceImpl),
        recording("first"),
        recording("second"))

      handler(sayHelloRequest()).futureValue
      events shouldBe List(
        s"first-before ${GreeterService.name}/SayHello",
        s"second-before ${GreeterService.name}/SayHello",
        "second-after",
        "first-after")
    }

    "not be defined for other services" in {
      val handler =
        ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), requireToken)
      handler.isDefinedAt(sayHelloRequest(service = "other.Service")) shouldBe false
      handler.isDefinedAt(sayHelloRequest()) shouldBe true
    }

    "map other exceptions to INTERNAL" in {
      val failing: ServerInterceptor = (_, _, _, _) => throw new RuntimeException("boom")
      val handler = ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), failing)

      grpcStatus(handler(sayHelloRequest()).futureValue) shouldBe Some(Status.Code.INTERNAL.value.toString)
    }

    "let outer interceptors see a synchronous throw from an inner interceptor as a failed future" in {
      @volatile var outerSawFailure = false
      val outer: ServerInterceptor = (_, _, request, next) =>
        next(request).recoverWith {
          case e =>
            outerSawFailure = true
            Future.failed(e)
        }(system.dispatcher)
      val throwing: ServerInterceptor = (_, _, _, _) => throw new GrpcServiceException(Status.UNAUTHENTICATED)
      val handler =
        ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), outer, throwing)

      grpcStatus(handler(sayHelloRequest()).futureValue) shouldBe Some(Status.Code.UNAUTHENTICATED.value.toString)
      outerSawFailure shouldBe true
    }

    "reject an invalid path under the service without calling the interceptors" in {
      val handler =
        ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), requireToken)
      val request = sayHelloRequest().withUri(s"https://localhost/${GreeterService.name}/SayHello/extra")

      grpcStatus(handler(request).futureValue) shouldBe Some(Status.Code.INVALID_ARGUMENT.value.toString)
    }

    "answer with unsupported media type when rejecting a non-gRPC request" in {
      val handler =
        ServerInterceptor.intercept(GreeterServiceHandler.partial(new CountingGreeterServiceImpl), requireToken)
      val request = HttpRequest(HttpMethods.POST, s"https://localhost/${GreeterService.name}/SayHello")

      handler(request).futureValue.status shouldBe StatusCodes.UnsupportedMediaType
    }

    "reject calls over the wire" in {
      //#all-services
      val handler = ServerInterceptor.intercept(
        ServiceHandler.concat(
          GreeterServiceHandler.partial(new CountingGreeterServiceImpl),
          ServerReflection.partial(List(GreeterService))),
        requireToken)
      //#all-services
      val server = Http().newServerAt("localhost", 0).bind(ServiceHandler.concatOrNotFound(handler)).futureValue
      val client = GreeterServiceClient(
        GrpcClientSettings.connectToServiceAt("localhost", server.localAddress.getPort).withTls(false))
      try {
        val failure = client.sayHello(HelloRequest("Alice")).failed.futureValue
        failure shouldBe a[StatusRuntimeException]
        failure.asInstanceOf[StatusRuntimeException].getStatus.getCode shouldBe Status.Code.UNAUTHENTICATED

        client
          .sayHello()
          .addHeader("token", "XYZ")
          .invoke(HelloRequest("Alice"))
          .futureValue
          .message shouldBe "Hi Alice!"
      } finally {
        client.close().futureValue
        server.terminate(3.seconds).futureValue
      }
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
