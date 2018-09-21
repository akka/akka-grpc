/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import java.security.KeyStore

import javax.net.ssl._
import play.core.ApplicationProvider
import play.core.server.ServerConfig
import play.core.server.ssl.FakeKeyStore
import play.server.api.SSLEngineProvider

// RICH: When merging into Play refactor the existing class in the Play integration tests
/**
 * Contains a statically initialized self-signed certificate.
 */
object SelfSigned {

  /**
   * The SSLContext and TrustManager associated with the self-signed certificate.
   */
  lazy val (sslContext, trustManager): (SSLContext, X509TrustManager) = {
    val keyStore: KeyStore = FakeKeyStore.generateKeyStore

    buildContextAndTrust(keyStore)
  }

  // TODO: this should be moved to `ssl-config`
  private def buildContextAndTrust(keyStore: KeyStore) = {

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(keyStore)
    val tms: Array[TrustManager] = tmf.getTrustManagers
    val x509TrustManager: X509TrustManager = tms(0).asInstanceOf[X509TrustManager]

    val kmf: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, Array.emptyCharArray)
    val kms: Array[KeyManager] = kmf.getKeyManagers

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(kms, tms, null)

    (sslContext, x509TrustManager)
  }
}

/**
 * An SSLEngineProvider which simply references the values in the
 * SelfSigned object.
 */
class SelfSignedSSLEngineProvider(serverConfig: ServerConfig, appProvider: ApplicationProvider) extends SSLEngineProvider {
  override def createSSLEngine: SSLEngine = SelfSigned.sslContext.createSSLEngine()
}