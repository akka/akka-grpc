/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.internal.GrpcMetadataImpl
import akka.grpc.{ GrpcClientSettings, Trailers }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, ExecutionContext, Future }

class RichErrorModelSpec
    extends TestKit(ActorSystem("RichErrorSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  import com.google.protobuf.any.Any
  import com.google.rpc.{ Code, Status }
  import io.grpc.protobuf.StatusProto

  implicit val sys: ActorSystem = system
  implicit val ec: ExecutionContext = sys.dispatcher

  object RichErrorImpl extends GreeterService {

    // #rich_error_model_unary
    private def toJavaProto(scalaPbSource: com.google.protobuf.any.Any): com.google.protobuf.Any = {
      val javaPbOut = com.google.protobuf.Any.newBuilder
      javaPbOut.setTypeUrl(scalaPbSource.typeUrl)
      javaPbOut.setValue(scalaPbSource.value)
      javaPbOut.build
    }

    def sayHello(in: HelloRequest): Future[HelloReply] = {
      val status: Status = Status
        .newBuilder()
        .setCode(Code.INVALID_ARGUMENT.getNumber)
        .setMessage("What is wrong?")
        .addDetails(toJavaProto(Any.pack(new HelloReply("The password!"))))
        .build()
      Future.failed(StatusProto.toStatusRuntimeException(status))
    }
    // #rich_error_model_unary

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = ???

    override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
  }

  val conf = ConfigFactory
    .load()
    //.parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  "Rich error model with the manual approach" should {
    "work" in {

      // #custom_eHandler
      val customHandler: PartialFunction[Throwable, Trailers] = {
        case grpcException: StatusRuntimeException =>
          Trailers(grpcException.getStatus, new GrpcMetadataImpl(grpcException.getTrailers))
      }

      val service: HttpRequest => Future[HttpResponse] =
        GreeterServiceHandler(RichErrorImpl, eHandler = (_: ActorSystem) => customHandler)
      // #custom_eHandler

      Http(system).newServerAt(interface = "127.0.0.1", port = 8080).bind(service)

      val client = GreeterServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false))

      // #client_request
      val richErrorResponse =
        Await.result(client.sayHello(HelloRequest("Bob")).failed, Duration(10, TimeUnit.SECONDS))

      val ex: StatusRuntimeException = (richErrorResponse match {
        case ex: StatusRuntimeException => Some(ex)
        case _                          => None
      }).get
      // #client_request

      // #client_rich_error_model
      val status: com.google.rpc.Status = StatusProto.fromStatusAndTrailers(ex.getStatus, ex.getTrailers)

      def fromJavaProto(javaPbSource: com.google.protobuf.Any): com.google.protobuf.any.Any =
        com.google.protobuf.any.Any(typeUrl = javaPbSource.getTypeUrl, value = javaPbSource.getValue)

      import HelloReply.messageCompanion
      val customErrorReply: HelloReply = fromJavaProto(status.getDetails(0)).unpack
      // #client_rich_error_model

      status.getCode should be(3)
      status.getMessage should be("What is wrong?")
      customErrorReply.message should be("The password!")
    }

  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

}
