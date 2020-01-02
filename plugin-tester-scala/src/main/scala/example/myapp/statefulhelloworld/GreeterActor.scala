/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.statefulhelloworld

import akka.actor.Actor
import akka.actor.Props

// #actor
object GreeterActor {
  case class ChangeGreeting(newGreeting: String)

  case object GetGreeting
  case class Greeting(greeting: String)

  def props(initialGreeting: String) = Props(new GreeterActor(initialGreeting))
}

class GreeterActor(initialGreeting: String) extends Actor {
  import GreeterActor._

  var greeting = Greeting(initialGreeting)

  def receive = {
    case GetGreeting => sender() ! greeting
    case ChangeGreeting(newGreeting) =>
      greeting = Greeting(newGreeting)
  }
}
// #actor
