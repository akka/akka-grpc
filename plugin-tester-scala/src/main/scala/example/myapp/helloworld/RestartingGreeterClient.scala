package example.myapp.helloworld

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.RestartingClient
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object RestartingGreeterClient {

  def main(args: Array[String]): Unit = {

    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = sys.dispatcher

    //#restarting-client
    // Function for creating a client
    val clientSettings = GrpcClientSettings.fromConfig(GreeterService.name)
    val clientConstructor = () => new GreeterServiceClient(clientSettings)

    // Wrapped in a restarting client
    val restartingClient = new RestartingClient[GreeterServiceClient](clientConstructor)
    //#restarting-client

    singleRequestReply()
    streamingRequest()
    streamingReply()
    streamingRequestReply()

    sys.scheduler.schedule(1.second, 1.second) {
      Try(singleRequestReply())
    }

    def singleRequestReply(): Unit = {
      sys.log.info("Performing request")

      //#usage
      val reply = restartingClient.withClient(c => c.sayHello(HelloRequest("Bob")))
      //#usage

      println(s"got single reply: ${Await.result(reply, 5.seconds).message}")
    }

    def streamingRequest(): Unit = {
      val requests = List("Alice", "Bob", "Peter").map(HelloRequest.apply)
      val reply = restartingClient.withClient(c => c.itKeepsTalking(Source(requests)))
      println(s"got single reply for streaming requests: ${Await.result(reply, 5.seconds).message}")
    }

    def streamingReply(): Unit = {
      val responseStream = restartingClient.withClient(c => c.itKeepsReplying(HelloRequest("Alice")))
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
      Await.ready(done, 1.minute) // just to keep sample simple - don't do Await.ready in actual code
    }

    def streamingRequestReply(): Unit = {
      val requestStream: Source[HelloRequest, NotUsed] =
        Source
          .tick(100.millis, 1.second, "tick")
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => HelloRequest(s"Alice-$i"))
          .take(10)
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[HelloReply, NotUsed] = restartingClient.withClient(c => c.streamHellos(requestStream))
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))
      Await.ready(done, 1.minute)
    }
  }

}

