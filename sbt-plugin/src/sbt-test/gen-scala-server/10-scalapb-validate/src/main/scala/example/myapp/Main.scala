package example.myapp

import scala.util.{Failure, Success, Try}
import scalapb.validate._

import example.myapp.helloworld.grpc.HelloRequest

object Main extends App {

  Try(HelloRequest("valid")) match {
    case Success(_) => // expected
    case Failure(e) => throw new RuntimeException("unexpected violations for \"valid\"", e)
  }

  Try(HelloRequest("ko")) match {
    case Success(_) => throw new RuntimeException("unexpected success for \"ko\"")
    case Failure(e) => // expected
  }

}
