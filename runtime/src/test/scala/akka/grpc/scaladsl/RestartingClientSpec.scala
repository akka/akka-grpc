/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.Done
import akka.grpc.internal.ClientConnectionException
import akka.grpc.scaladsl.RestartingClient.ClientClosedException
import akka.grpc.scaladsl.RestartingClientSpec.FakeClient
import org.scalatest.Inspectors._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Span }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._

import akka.testkit._

object RestartingClientSpec {

  object FakeClient {
    val count = new AtomicInteger(1)
  }

  class FakeClient extends AkkaGrpcClient {

    private val nr = FakeClient.count.getAndIncrement()
    private val promise: Promise[Done] = Promise[Done]()
    @volatile var beenClosed: Boolean = false

    override def closed(): Future[Done] = promise.future

    def bestClientCallEver(): String = "best"

    override def close(): Future[Done] = {
      println(s"$nr Close()")
      beenClosed = true
      Future.successful(Done)
    }

    def fail(t: Throwable): Unit = {
      println(s"$nr Fail(): " + t)
      beenClosed = true
      promise.tryFailure(t)
    }

    def succeed(): Unit = {
      println(s"$nr Succeed()")
      promise.trySuccess(Done)
    }

    override def toString = s"FakeClient($nr, $promise, $beenClosed)"
  }

}

class RestartingClientSpec extends WordSpec with Matchers with ScalaFutures with Eventually with BeforeAndAfterAll {

  implicit val patience: PatienceConfig = PatienceConfig(timeout = Span(500, Millis), interval = Span(20, Millis))
  implicit val system = ActorSystem("RestartingCientSpec")
  val queueTimeoutMs = 25.milliseconds.dilated.toMillis

  def fakeRestartingClient(capacity: Int = 2): (BlockingQueue[FakeClient], RestartingClient[FakeClient]) = {
    val clientCreations = new ArrayBlockingQueue[FakeClient](capacity)
    val restartingClient = RestartingClient(() => {
      val c = new FakeClient
      require(clientCreations.offer(c))
      c
    })
    (clientCreations, restartingClient)
  }

  "Restarting client" must {
    "re-create client if fails" in {
      val (clientCreations, restartingClient) = fakeRestartingClient()
      val firstClient = clientCreations.poll(queueTimeoutMs, TimeUnit.MILLISECONDS)
      firstClient shouldNot be(null)
      clientCreations.size() should be(0)
      restartingClient.withClient(c => c should be(firstClient))

      firstClient.fail(new ClientConnectionException("Oh noes"))

      val secondClient = clientCreations.poll(queueTimeoutMs, TimeUnit.MILLISECONDS)
      secondClient shouldNot be(null)
      clientCreations.size() should be(0)
      eventually {
        restartingClient.withClient(c => c should be(secondClient))
      }
    }

    "not re-create client if it closes down" in {
      val (clientCreations, restartingClient) = fakeRestartingClient()
      val firstClient = clientCreations.poll(queueTimeoutMs, TimeUnit.MILLISECONDS)
      firstClient shouldNot be(null)
      clientCreations.size() should be(0)
      restartingClient.withClient(c => c should be(firstClient))

      firstClient.succeed()

      val secondClient = clientCreations.poll(queueTimeoutMs, TimeUnit.MILLISECONDS)
      secondClient should be(null)
      clientCreations.size() should be(0)
      restartingClient.withClient(c => c should be(firstClient))
    }

    "not catch user exceptions on creation" in {
      intercept[RuntimeException] {
        new RestartingClient[FakeClient](() => throw new RuntimeException("I don't want to create a client"))
      }
    }

    "not re-create on any other exceptions" in {
      val (clientCreations, restartingClient) = fakeRestartingClient()
      restartingClient.withClient(c => c.fail(new RuntimeException("Naughty")))
      val firstClient = clientCreations.poll(queueTimeoutMs, TimeUnit.MILLISECONDS)
      firstClient shouldNot be(null)
      clientCreations.size() should be(0)

      // gets closed on a future callback so give it some time
      eventually {
        intercept[ClientClosedException] {
          restartingClient.withClient(c => c.bestClientCallEver())
        }
      }
    }

    "execute code for via the client" in {
      val (_, restartingClient) = fakeRestartingClient()
      restartingClient.withClient(f => f.bestClientCallEver()) shouldEqual "best"
    }

    "close all the clients" in {
      val (clientCreations, restartingClient) = fakeRestartingClient(20)
      val failures = Future.sequence((1 to 20).map(i => Future {
        try {
          restartingClient.withClient(c => {
            c.fail(new ClientConnectionException(s"fail-$i"))
          })
        } catch {
          case _: ClientClosedException =>
        }
      }))

      val closers = Future.sequence((1 to 5).map(_ => Future {
        restartingClient.close()
      }))

      failures.futureValue
      closers.futureValue
      eventually {
        val clients = clientCreations.asScala
        forAll(clients) { c => c.beenClosed shouldBe true }
      }
    }
  }

  override def afterAll() {
    super.afterAll()
    Await.result(system.terminate(), 10.seconds)
  }
}
