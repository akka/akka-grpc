/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import java.util.concurrent.locks.Lock

import play.api.{Application, Configuration, Mode}
import play.core.server.{AkkaHttpServer, ServerConfig}

import scala.util.control.NonFatal

trait TestServerFactory {
  def start(app: Application): NewTestServer
}

object DefaultTestServerFactory extends DefaultTestServerFactory

class DefaultTestServerFactory extends TestServerFactory {

  override def start(app: Application): NewTestServer = {
    val testServer: play.api.test.TestServer = new play.api.test.TestServer(
      serverConfig(app), app, Some(serverProvider(app))
    )

    // TODO: Maybe move this logic into Helpers
    val appLock: Option[Lock] = optionalGlobalLock(app)
    appLock.foreach(_.lock())
    val stopServer = new AutoCloseable {
      override def close(): Unit = {
        testServer.stop()
        appLock.foreach(_.unlock())
      }
    }

    try {
      testServer.start()
      NewTestServer(testServer, serverEndpoints(testServer), stopServer)
    } catch {
      case NonFatal(e) =>
        stopServer.close()
        throw e
    }
  }

  protected def optionalGlobalLock(app: Application): Option[Lock] = {
    if (app.globalApplicationEnabled) Some(PlayRunners.mutex) else None
  }

  protected def serverConfig(app: Application) = {
    val sc = ServerConfig(
      port = Some(0),
      sslPort = Some(0),
      mode = Mode.Test,
      rootDir = app.path
    )
    sc.copy(configuration = sc.configuration ++ overrideServerConfiguration(app))
  }

  protected def overrideServerConfiguration(app: Application): Configuration = Configuration(
    "play.server.https.engineProvider" -> classOf[SelfSignedSSLEngineProvider].getName,
    "play.server.akka.http2.enabled" -> true,
  )

  protected def serverProvider(app: Application): play.core.server.ServerProvider = AkkaHttpServer.provider

  protected def serverEndpoints(testServer: play.api.test.TestServer): ServerEndpoints = {
    val httpEndpoint: Option[ServerEndpoint] = testServer.runningHttpPort.map(port => ServerEndpoint(
      scheme = "http",
      host = "localhost",
      port = port,
      httpVersions = Set("1.0", "1.1"),
      ssl = None
    ))
    val httpsEndpoint: Option[ServerEndpoint] = testServer.runningHttpsPort.map(port => ServerEndpoint(
      scheme = "https",
      host = "localhost",
      port = port,
      httpVersions = Set("1.0", "1.1", "2.0"),
      ssl = Some(ServerEndpoint.ClientSsl(SelfSigned.sslContext, SelfSigned.trustManager))
    ))
    ServerEndpoints(httpEndpoint.toSeq ++ httpsEndpoint.toSeq)
  }

}