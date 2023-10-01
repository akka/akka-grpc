package example.myapp.shelf

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.JsObject
import spray.json.DefaultJsonProtocol._

import scala.concurrent.ExecutionContextExecutor
import scala.compat.java8.FutureConverters._

class ShelfServiceHttpTranscodingSpec
    extends ScalaTestWithActorTestKit(config = "akka.http.server.enable-http2 = on")
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val serverWithHttpTranscoding = ShelfServer.run(system.classicSystem).toScala.futureValue

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
