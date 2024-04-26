//#full-example
package com.example.helloworld

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class GreeterServiceImplSpec
  extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  val testKit = ActorTestKit()

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  implicit val system: ActorSystem[_] = testKit.system

  val service = new GreeterServiceImpl(system)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "GreeterServiceImpl" should {
    "reply to single request" in {
      val reply = service.sayHello(HelloRequest("Bob"))
      reply.futureValue should ===(HelloReply("Hello, Bob"))
    }
  }
}
//#full-example
