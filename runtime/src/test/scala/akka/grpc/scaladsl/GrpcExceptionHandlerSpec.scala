/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.internal.GrpcResponseHelpers
import akka.grpc.scaladsl.GrpcExceptionHandler.{ default, defaultMapper }
import akka.http.scaladsl.model.HttpEntity._
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import io.grpc.Status
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.{ ExecutionException, Future }

class GrpcExceptionHandlerSpec extends WordSpec with Matchers with ScalaFutures {
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()

  val expected: Function[Throwable, Status] = {
    case e: ExecutionException =>
      if (e.getCause == null) Status.INTERNAL
      else expected(e.getCause)
    case grpcException: GrpcServiceException => grpcException.status
    case _: NotImplementedError              => Status.UNIMPLEMENTED
    case _: UnsupportedOperationException    => Status.UNIMPLEMENTED
    case other                               => Status.INTERNAL
  }

  val otherTypes: Seq[Throwable] = Seq(
    new GrpcServiceException(status = Status.DEADLINE_EXCEEDED),
    new NotImplementedError,
    new UnsupportedOperationException,
    new NullPointerException,
    new RuntimeException)

  val executionExceptions: Seq[Throwable] =
    otherTypes.map(new ExecutionException(_)) :+ new ExecutionException("doh", null)

  "defaultMapper" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      val exp = expected(e)
      s"Map $e to $exp" in {
        defaultMapper(system)(e) shouldBe exp
      }
    }
  }

  "default(defaultMapper)" should {
    (otherTypes ++ executionExceptions).foreach { e =>
      s"Correctly map $e" in {
        val exp = GrpcResponseHelpers.status(defaultMapper(system)(e))
        val expChunks = getChunks(exp)
        val act = default(defaultMapper(system))(system)(e).futureValue
        val actChunks = getChunks(act)
        // Following is because aren't equal
        act.status shouldBe exp.status
        actChunks.toString shouldEqual expChunks.toString
      }
    }
  }

  def getChunks(resp: HttpResponse): Seq[ChunkStreamPart] =
    (resp.entity match {
      case Chunked(contentType, chunks) =>
        chunks.runFold(Seq.empty[ChunkStreamPart]) { case (seq, chunk) => seq :+ chunk }
      case _ => Future.successful(Seq.empty[ChunkStreamPart])
    }).futureValue
}
