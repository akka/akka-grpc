package play.api.test // Experimental API changes

import java.security.KeyStore
import java.util.concurrent.locks.Lock

import javax.net.ssl._
import play.api.{Application, Configuration, Mode}
import play.core.ApplicationProvider
import play.core.server.{AkkaHttpServer, ServerConfig}
import play.core.server.ssl.FakeKeyStore
import play.server.api.SSLEngineProvider

import scala.util.control.NonFatal

///////////////////////////// Endpoints /////////////////////////

/**
 * Contains information about which port and protocol can be used to connect to the server.
 * This class is used to abstract out the details of connecting to different backends
 * and protocols. Most tests will operate the same no matter which endpoint they
 * are connected to.
 */
final case class ServerEndpoint(
    scheme: String,
    host: String,
    port: Int,
    httpVersions: Set[String],
    ssl: Option[ServerEndpoint.ClientSsl]
  ) {

  /**
   * Create a full URL out of a path. E.g. a path of `/foo` becomes `http://localhost:12345/foo`
   */
  final def pathUrl(path: String): String = s"$scheme://$host:$port$path"

}

object ServerEndpoint {
  /** Contains information how SSL is configured for an [[ServerEndpoint]]. */
  case class ClientSsl(sslContext: SSLContext, trustManager: X509TrustManager)
}

case class ServerEndpoints(endpoints: Seq[ServerEndpoint]) {
  private def endpointForScheme(scheme: String): Option[ServerEndpoint] = endpoints.filter(_.scheme == scheme).headOption
  /** Convenient way to get an HTTP endpoint */
  val httpEndpoint: Option[ServerEndpoint] = endpointForScheme("http")
  /** Convenient way to get an HTTPS endpoint */
  val httpsEndpoint: Option[ServerEndpoint] = endpointForScheme("https")
}

///////// TestServer /////

// TODO: Merge this functionality into TestServer itself
final case class NewTestServer(
    testServer: TestServer,
    endpoints: ServerEndpoints,
    stopServer: AutoCloseable)

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
/// SSLEngine //////

/**
 * An SSLEngineProvider which simply references the values in the
 * SelfSigned object.
 */
class SelfSignedSSLEngineProvider(serverConfig: ServerConfig, appProvider: ApplicationProvider) extends SSLEngineProvider {
  override def createSSLEngine: SSLEngine = SelfSigned.sslContext.createSSLEngine()
}

/**
 * Contains a statically initialized self-signed certificate.
 */
object SelfSigned {

  /**
   * The SSLContext and TrustManager associated with the self-signed certificate.
   */
  lazy val (sslContext, trustManager): (SSLContext, X509TrustManager) = {
    val keyStore: KeyStore = FakeKeyStore.generateKeyStore

    val kmf: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    kmf.init(keyStore, "".toCharArray)
    val kms: Array[KeyManager] = kmf.getKeyManagers

    val tmf: TrustManagerFactory = TrustManagerFactory
      .getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)
    val tms: Array[TrustManager] = tmf.getTrustManagers
    val x509TrustManager: X509TrustManager = tms(0).asInstanceOf[X509TrustManager]

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(kms, tms, null)

    (sslContext, x509TrustManager)
  }
}