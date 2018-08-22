/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import org.specs2.execute.{ AsResult, Result }
import org.specs2.specification.{ ForEach, Scope }
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

// RICH: replacement for play-specs2's WithServer class; adding server endpoint info
class NewForServer(
  val app: Application = GuiceApplicationBuilder().build(),
  testServerFactory: TestServerFactory = new DefaultTestServerFactory) extends ForEach[RunningServer] with Scope {

  override protected def foreach[R: AsResult](f: RunningServer => R): Result = {
    val ts: NewTestServer = testServerFactory.start(app)
    try AsResult.effectively(f(RunningServer(app, ts.endpoints))) finally ts.stopServer.close()
  }
}