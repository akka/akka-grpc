/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import org.specs2.execute.{ AsResult, Result }
import org.specs2.specification.{ ForEach, Scope }
import play.api.Application

// RICH: replacement for play-specs2's WithServer class; adding server endpoint info
trait NewForServer extends ForEach[RunningServer] with Scope {

  protected def applicationFactory: ApplicationFactory
  protected def testServerFactory: TestServerFactory = new DefaultTestServerFactory()

  override protected def foreach[R: AsResult](f: RunningServer => R): Result = {
    val app: Application = applicationFactory.create()
    val ts: NewTestServer = testServerFactory.start(app)
    try AsResult.effectively(f(RunningServer(app, ts.endpoints))) finally ts.stopServer.close()
  }
}
