package example.myapp

import scalapb.validate._

import example.myapp.helloworld.grpc.HelloRequest

object Main extends App {

  val valid = HelloRequest("valid")
  val ko    = HelloRequest("ko")

  val requestValidator = Validator[HelloRequest]

  requestValidator.validate(valid) match {
    case Success             => // expected
    case Failure(violations) => throw new RuntimeException(s"unexpected violations $violations for $valid")
  }

  requestValidator.validate(ko) match {
    case Success    => throw new RuntimeException(s"unexpected success for $ko")
    case Failure(_) => // expected
  }

}
