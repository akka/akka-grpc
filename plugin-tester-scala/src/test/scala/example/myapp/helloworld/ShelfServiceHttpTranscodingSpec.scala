package example.myapp.helloworld

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import example.myapp.shelf.ShelfServer
import example.myapp.shelf.grpc.{ GetShelfRequest, ShelfServiceClient }
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
          .flatMap(response => Unmarshal(response).to[JsObject])
          .futureValue
      shelf.fields("id").convertTo[String] shouldBe "1"
    }

  }

}
