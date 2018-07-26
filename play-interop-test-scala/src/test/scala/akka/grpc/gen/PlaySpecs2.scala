package play.api.test /* Use this file for testing out API changes */

import org.specs2.execute.{ AsResult, Result }
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait HasEndpoint {
  implicit protected def implicitEndpoint: ServerEndpoint
}

abstract class NewWithServer(
  val app: Application = GuiceApplicationBuilder().build(),
  testServerFactory: TestServerFactory = new DefaultTestServerFactory) extends Around with Scope {

  implicit def implicitMaterializer = app.materializer
  implicit def implicitApp = app
  implicit def implicitEndpoint: ServerEndpoint = synchronized {
    assert(runningServer != null, "Can't get server endpoint because test server is not running")
    runningServer.endpoints.endpoints.head
  }

  // Used by WSTestClient.wsCall/wsUrl
  implicit final def implicitPort: Port = implicitEndpoint.port
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