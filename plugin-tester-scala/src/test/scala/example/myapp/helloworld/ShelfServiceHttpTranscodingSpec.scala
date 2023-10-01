package example.myapp.helloworld

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import example.myapp.shelf.ShelfServer
import example.myapp.shelf.grpc.{ GetShelfRequest, Shelf, ShelfServiceClient }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContextExecutor

class ShelfServiceHttpTranscodingSpec
    extends ScalaTestWithActorTestKit(config = "akka.http.server.enable-http2 = on")
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val shelfFormat: RootJsonFormat[Shelf] = new RootJsonFormat[Shelf] {
    def write(obj: Shelf): JsValue = {
      JsObject("id" -> JsString(obj.id.toString), "theme" -> JsString(obj.theme))
    }

    def read(json: JsValue): Shelf = {
      val jso = json.convertTo[JsObject]
      val id = jso.fields("id").convertTo[String].toLong
      val theme = jso.fields("theme").convertTo[String]
      Shelf(id, theme)
    }
  }

  val serverWithHttpTranscoding = new ShelfServer().run().futureValue
  val clientConfig = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false)
  val client = ShelfServiceClient(clientConfig)

  "a gRPC server with HTTP transcoding enabled should" should {

    "able to answer normal gRPC call" in {
      val shelf = client.getShelf(GetShelfRequest(1))
      shelf.futureValue.id shouldBe 1
    }

    "able to answer http call" in {
      val shelf =
        Http()
          .singleRequest(Get("http://localhost:8080/v1/shelves/1"))
          .flatMap(response => Unmarshal(response).to[Shelf])
          .futureValue
      shelf.id shouldBe 1
    }

  }

}
