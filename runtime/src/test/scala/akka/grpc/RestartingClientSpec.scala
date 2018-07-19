package akka.grpc

import java.util.concurrent._

import akka.Done
import akka.grpc.RestartingClient.ClientClosedException
import akka.grpc.RestartingClientSpec.FakeClient
import akka.grpc.internal.{AkkaGrpcClient, ClientConnectionException}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.Inspectors._

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

object RestartingClientSpec {

  class FakeClient extends AkkaGrpcClient {

    private val promise: Promise[Done] = Promise[Done]()
    @volatile var beenClosed: Boolean = false

    override def closed(): Future[Done] = promise.future

    def bestClientCallEver(): String = "best"

    override def close(): Future[Done] = {
      beenClosed = true
      Future.successful(Done)
    }

    def fail(t: Throwable): Unit = {
      promise.tryFailure(t)
      beenClosed = true
    }

    def succeed(): Unit = {
      promise.trySuccess(Done)
    }

    override def toString = s"FakeClient($promise, $beenClosed)"
  }

}

class RestartingClientSpec extends WordSpec with Matchers with ScalaFutures with Eventually {

  val queueTimeoutMs = 10

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

    "not re-create on any other exceptions"in {
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
        restartingClient.withClient(f => {
          try {
            f.fail(new ClientConnectionException(s"fail-$i"))
          } catch {
            case _: ClientClosedException =>
          }
        })
      }))
      val closers = Future.sequence((1 to 5).map(_ => Future {
        restartingClient.close()
      }))

      failures.futureValue
      closers.futureValue
      val clients = clientCreations.asScala
      forAll(clients) { c => c.beenClosed shouldBe true }
    }
  }
}
