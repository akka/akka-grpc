/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitSuite
import org.scalatest.time.{ Millis, Span }

abstract class JUnitEventually extends JUnitSuite with Eventually {
  implicit val patience: PatienceConfig = PatienceConfig(timeout = Span(500, Millis), interval = Span(20, Millis))
  def junitEventually[T](fun: => T): T = eventually(fun)
}
