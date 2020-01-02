/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.statefulhelloworld

import example.myapp.statefulhelloworld.grpc.GreeterService
import example.myapp.statefulhelloworld.grpc.{ ChangeRequest, ChangeResponse, HelloReply, HelloRequest }
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import scala.concurrent.Future

// #stateful-service
class GreeterServiceImpl(system: ActorSystem) extends GreeterService {
  val greeterActor = system.actorOf(GreeterActor.props("Hello"), "greeter")

  def sayHello(in: HelloRequest): Future[HelloReply] = {
    // timeout and execution context for ask
    implicit val timeout: Timeout = 3.seconds
    import system.dispatcher

    (greeterActor ? GreeterActor.GetGreeting)
      .mapTo[GreeterActor.Greeting]
      .map(message => HelloReply(s"${message.greeting}, ${in.name}"))
  }

  def changeGreeting(in: ChangeRequest): Future[ChangeResponse] = {
    greeterActor ! GreeterActor.ChangeGreeting(in.newGreeting)
    Future.successful(ChangeResponse())
  }
}
// #stateful-service
