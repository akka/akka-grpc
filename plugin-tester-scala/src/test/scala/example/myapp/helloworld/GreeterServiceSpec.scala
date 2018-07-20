package example.myapp.helloworld

import scala.concurrent.Await

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.SSLContextUtils
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span

import example.myapp.helloworld.grpc._

class GreeterSpec
  extends Matchers
    with WordSpecLike
    with BeforeAndAfterAll
    with ScalaFutures {

  implicit val patience = PatienceConfig(5.seconds, Span(100, org.scalatest.time.Millis))

  val serverSystem: ActorSystem = {
    // important to enable HTTP/2 in server ActorSystem's config
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val sys = ActorSystem("GreeterServer", conf)
    val bound = new GreeterServer(sys).run()
    // make sure server is bound before using client
    bound.futureValue
    sys
  }

  val clientSystem = ActorSystem("GreeterClient")

  val client = {
    implicit val mat = ActorMaterializer.create(clientSystem)
    implicit val ec = clientSystem.dispatcher
    new GreeterServiceClient(
      GrpcClientSettings("127.0.0.1", 8080)
        .withOverrideAuthority("foo.test.google.fr")
        .withSSLContext(SSLContextUtils.sslContextFromResource("/certs/ca.pem")))
  }

  override def afterAll: Unit = {
    Await.ready(clientSystem.terminate(), 5.seconds)
    Await.ready(serverSystem.terminate(), 5.seconds)
  }

  "GreeterService" should {
    "reply to single request" in {
      val reply = client.sayHello(HelloRequest("Alice"))
      reply.futureValue should ===(HelloReply("Hello, Alice"))
    }
  }
}
