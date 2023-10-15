/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.shelf

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class ShelfServiceHttpTranscodingSpec extends AnyWordSpecLike with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val patience: PatienceConfig =
    PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  implicit val serverSystem: ActorSystem = {
    // important to enable HTTP/2 in server ActorSystem's config
    val conf =
      ConfigFactory.parseString("akka.http.server.enable-http2 = on").withFallback(ConfigFactory.defaultApplication())
    val sys = ActorSystem("ShelfServer", conf)
    // make sure servers are bound before using client
    ShelfServer.run(sys).toCompletableFuture.get
    sys
  }

  implicit val ec: ExecutionContextExecutor = serverSystem.dispatcher

  "a gRPC server with HTTP transcoding enabled" should {

    "able to answer http call" in {
      val shelf =
        Http()
          .singleRequest(Get("http://localhost:8080/v1/shelves/1"))
          .flatMap(response => Unmarshal(response).to[JsObject])
          .futureValue
      shelf.fields("id").convertTo[String] shouldBe "1"
    }
  }

}
