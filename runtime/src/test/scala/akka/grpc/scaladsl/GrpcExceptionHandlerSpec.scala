/*
 * Copyright (C) 2019-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcProtocol
import akka.grpc.GrpcServiceException
import akka.grpc.internal.{ GrpcProtocolNative, GrpcResponseHelpers, Identity }
import akka.grpc.scaladsl.GrpcExceptionHandler.defaultMapper
import akka.http.scaladsl.model.HttpEntity._
import akka.http.scaladsl.model.HttpResponse
import io.grpc.{ Status, StatusRuntimeException }
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ ExecutionException, Future }

class GrpcExceptionHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("Test")
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))
  implicit val writer: GrpcProtocol.GrpcProtocolWriter = GrpcProtocolNative.newWriter(Identity)

  val expected: Function[Throwable, Status] = {
    case e: ExecutionException =>
      if (e.getCause == null) Status.INTERNAL
      else expected(e.getCause)
    case grpcException: GrpcServiceException => grpcException.status
    case e: StatusRuntimeException           => e.getStatus
    case e: NotImplementedError              => Status.UNIMPLEMENTED.withDescription(e.getMessage)
    case e: UnsupportedOperationException    => Status.UNIMPLEMENTED.withDescription(e.getMessage)
    case _                                   => Status.INTERNAL
  }

  val otherTypes: Seq[Throwable] = Seq(
    new GrpcServiceException(status = Status.DEADLINE_EXCEEDED),
    new NotImplementedError,
    new UnsupportedOperationException,
    new NullPointerException,
    new RuntimeException,
    new StatusRuntimeException(io.grpc.Status.DEADLINE_EXCEEDED))

  val executionExceptions: Seq[Throwable] =
    otherTypes.map(new ExecutionException(_)) :+ new ExecutionException("doh", null)

  "defaultMapper" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      val exp = expected(e)
      s"Map $e to $exp" in {
        val status = defaultMapper(system)(e).status
        // according to io.grpc.Status.equals javadoc, equals is not well-defined for Status instances, so
        // comparing code and description explicitly
        status.getCode shouldBe exp.getCode
        status.getDescription shouldBe exp.getDescription
      }
    }
  }

  "default(defaultMapper)" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      s"Correctly map $e" in {
        val exp = GrpcResponseHelpers.status(defaultMapper(system)(e))
        val expChunks = getChunks(exp)
        val act = GrpcExceptionHandler.from(defaultMapper(system))(system, writer)(e).futureValue
        val actChunks = getChunks(act)
        // Following is because aren't equal
        act.status shouldBe exp.status
        actChunks.toString shouldEqual expChunks.toString
      }
    }
  }

  def getChunks(resp: HttpResponse): Seq[ChunkStreamPart] =
    (resp.entity match {
      case Chunked(_, chunks) =>
        chunks.runFold(Seq.empty[ChunkStreamPart]) { case (seq, chunk) => seq :+ chunk }
      case _ => Future.successful(Seq.empty[ChunkStreamPart])
    }).futureValue

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
