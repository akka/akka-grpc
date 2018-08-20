/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import org.specs2.execute.{ AsResult, Result }
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

// RICH: replacement for play-specs2's WithServer class; adding serverEndpoints
// RICH: the constructor has been refactored; when merging into Play there will
// need to be more consideration for backwards compatibility (should be doable)
abstract class NewWithServer(
  val app: Application = GuiceApplicationBuilder().build(),
  testServerFactory: TestServerFactory = new DefaultTestServerFactory) extends Around with Scope {

  implicit def implicitMaterializer = app.materializer
  implicit def implicitApp = app

  /**
   * The list of endpoints of the currently running test server.
   */
  protected def serverEndpoints: ServerEndpoints = synchronized {
    assert(runningServer != null, "Can't get server endpoints because test server is not running")
    runningServer.endpoints
  }

  /**
   * The default endpoint of the currently running test server.
   */
  implicit protected def implicitEndpoint: ServerEndpoint = serverEndpoints.endpoints.head

  // RICH: Changed to make this final
  // Used by WSTestClient.wsCall/wsUrl
  implicit final def implicitPort: Port = implicitEndpoint.port

  // RICH: Not sure if the following implicits are useful or not; needs more investigation
  // Used by WSTestClient.withClient
  implicit final def implicitHttpPort(implicit endpoint: ServerEndpoint): play.api.http.Port = new play.api.http.Port(endpoint.port)
  // Used by WSTestClient.wsCall/wsUrl/withClient
  implicit final def implicitScheme(implicit endpoint: ServerEndpoint): String = endpoint.scheme

  @volatile private var runningServer: NewTestServer = _

  override def around[T: AsResult](t: => T): Result = {
    synchronized {
      assert(runningServer == null)
      runningServer = testServerFactory.start(app)
    }
    try AsResult.effectively(t) finally {
      synchronized {
        runningServer.stopServer.close()
        runningServer = null
      }
    }
  }

}