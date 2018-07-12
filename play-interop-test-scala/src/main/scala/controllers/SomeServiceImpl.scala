/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package controllers

import example.myapp.someservice.grpc.anotherService.{ SomeReply, SomeRequest, SomeService }

import scala.concurrent.Future

// just a dummy for now, needed until #291 is merged
class SomeServiceImpl extends SomeService {
  def doSomething(in: SomeRequest): Future[SomeReply] = ???
}
