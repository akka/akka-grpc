package example.myapp.helloworld

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import example.myapp.shelf.ShelfServer
import example.myapp.shelf.grpc._
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

  val serverWithHttpTranscoding = ShelfServer.run(system).futureValue
  val clientConfig = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false).withBackend("akka-http")
  val client = ShelfServiceClient(clientConfig)

  val HalloweenShelf = Shelf(1, "halloween")

  "a gRPC server with HTTP transcoding enabled should" should {

    "able to handel gRPC call" in {
      val createResponse = client.createShelf(CreateShelfRequest(Some(HalloweenShelf)))

      createResponse.futureValue shouldBe HalloweenShelf

      val deleteResponse = client.deleteShelf(DeleteShelfRequest(1))

      deleteResponse.futureValue shouldBe HalloweenShelf
    }

    "able to handle http call" in {
      val createResponse = Http()
        .singleRequest(Post("http://localhost:8080/v1/shelves", HalloweenShelf))
        .flatMap(response => Unmarshal(response).to[Shelf])

      createResponse.futureValue shouldBe HalloweenShelf

      val getResponse =
        Http()
          .singleRequest(Get("http://localhost:8080/v1/shelves/1"))
          .flatMap(response => Unmarshal(response).to[Shelf])

      getResponse.futureValue shouldBe HalloweenShelf

      val deleteResponse = Http()
        .singleRequest(Delete("http://localhost:8080/v1/shelves/1"))
        .flatMap(response => Unmarshal(response).to[Shelf])

      deleteResponse.futureValue shouldBe HalloweenShelf
    }

    "able to report error" in {
      val response = Http().singleRequest(Get("http://localhost:8080/v1/shelves/1")).futureValue

      response.status shouldBe StatusCodes.NotFound
    }

  }

}
