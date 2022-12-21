/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.{ GrpcClientSettings, GrpcServiceException }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestKit
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.typesafe.config.ConfigFactory
import example.myapp.helloworld.grpc.helloworld._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class RichErrorModelNativeSpec
    extends TestKit(ActorSystem("RichErrorNativeSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, Span(10, org.scalatest.time.Millis))

  implicit val sys: ActorSystem = system
  implicit val ec: ExecutionContext = sys.dispatcher

  object RichErrorNativeImpl extends GreeterService {

    // #rich_error_model_unary
    def sayHello(in: HelloRequest): Future[HelloReply] = {
      Future.failed(
        GrpcServiceException(Code.INVALID_ARGUMENT, "What is wrong?", Seq(new LocalizedMessage("EN", "The password!"))))
    }
    // #rich_error_model_unary

    def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
      Source.failed(
        GrpcServiceException(Code.INVALID_ARGUMENT, "What is wrong?", Seq(new LocalizedMessage("EN", "The password!"))))
    }

    override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = {
      in.runWith(Sink.seq).flatMap { _ =>
        Future.failed(
          GrpcServiceException(
            Code.INVALID_ARGUMENT,
            "What is wrong?",
            Seq(new LocalizedMessage("EN", "The password!"))))
      }
    }

    override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
      Source.failed(
        GrpcServiceException(Code.INVALID_ARGUMENT, "What is wrong?", Seq(new LocalizedMessage("EN", "The password!"))))
    }
  }

  val service: HttpRequest => Future[HttpResponse] =
    GreeterServiceHandler(RichErrorNativeImpl)

  val bound =
    Http(system).newServerAt(interface = "127.0.0.1", port = 0).bind(service).futureValue

  val client = GreeterServiceClient(
    GrpcClientSettings.connectToServiceAt("127.0.0.1", bound.localAddress.getPort).withTls(false))

  val conf = ConfigFactory.load().withFallback(ConfigFactory.defaultApplication())

  "Rich error model" should {

    "work with the native api on a unary call" in {

      // #client_request
      val richErrorResponse = client.sayHello(HelloRequest("Bob")).failed.futureValue

      richErrorResponse match {
        case status: GrpcServiceException =>
          status.metadata match {
            case richMetadata: RichMetadata =>
              richMetadata.details(0).typeUrl should be("type.googleapis.com/google.rpc.LocalizedMessage")

              import LocalizedMessage.messageCompanion
              val localizedMessage: LocalizedMessage = richMetadata.getParsedDetails(0)
              localizedMessage.message should be("The password!")
              localizedMessage.locale should be("EN")

              richMetadata.code should be(3)
              richMetadata.message should be("What is wrong?")

            case other => fail(s"This should be a RichGrpcMetadataImpl but it is ${other.getClass}")
          }

        case ex => fail(s"This should be a GrpcServiceException but it is ${ex.getClass}")
      }
      // #client_request
    }

    "work with the native api on a stream request" in {

      val requests = List("Alice", "Bob", "Peter").map(HelloRequest(_))

      val richErrorResponse = client.itKeepsTalking(Source(requests)).failed.futureValue

      richErrorResponse match {
        case status: GrpcServiceException =>
          status.metadata match {
            case metadata: RichMetadata =>
              metadata.details(0).typeUrl should be("type.googleapis.com/google.rpc.LocalizedMessage")

              import LocalizedMessage.messageCompanion
              val localizedMessage: LocalizedMessage = metadata.getParsedDetails(0)

              metadata.code should be(3)
              metadata.message should be("What is wrong?")
              localizedMessage.message should be("The password!")
              localizedMessage.locale should be("EN")

            case other => fail(s"This should be a RichGrpcMetadataImpl but it is ${other.getClass}")
          }

        case ex => fail(s"This should be a GrpcServiceException but it is ${ex.getClass}")
      }

    }

    "work with the native api on a stream response" in {
      val richErrorResponseStream = client.itKeepsReplying(HelloRequest("Bob"))
      val richErrorResponse =
        richErrorResponseStream.run().failed.futureValue

      richErrorResponse match {
        case status: GrpcServiceException =>
          status.metadata match {
            case metadata: RichMetadata =>
              metadata.details(0).typeUrl should be("type.googleapis.com/google.rpc.LocalizedMessage")

              val localizedMessage = metadata.getParsedDetails[LocalizedMessage](0)

              metadata.code should be(3)
              metadata.message should be("What is wrong?")
              localizedMessage.message should be("The password!")
              localizedMessage.locale should be("EN")

            case other => fail(s"This should be a RichGrpcMetadataImpl but it is ${other.getClass}")
          }
        case ex => fail(s"This should be a GrpcServiceException but it is ${ex.getClass}")
      }

    }

    "work with the native api on a bidi stream" in {

      val requests = List("Alice", "Bob", "Peter").map(HelloRequest(_))
      val richErrorResponseStream = client.streamHellos(Source(requests))
      val richErrorResponse =
        richErrorResponseStream.run().failed.futureValue

      richErrorResponse match {
        case status: GrpcServiceException =>
          status.metadata match {
            case metadata: RichMetadata =>
              metadata.details(0).typeUrl should be("type.googleapis.com/google.rpc.LocalizedMessage")

              val localizedMessage = metadata.getParsedDetails[LocalizedMessage](0)

              metadata.code should be(3)
              metadata.message should be("What is wrong?")
              localizedMessage.message should be("The password!")
              localizedMessage.locale should be("EN")

            case other => fail(s"This should be a RichGrpcMetadataImpl but it is ${other.getClass}")
          }
        case ex => fail(s"This should be a GrpcServiceException but it is ${ex.getClass}")
      }

    }

  }

  override def afterAll(): Unit = system.terminate().futureValue
}
