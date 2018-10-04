/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import java.security.KeyStore

import javax.net.ssl._

import com.typesafe.sslconfig.ssl.FakeSSLTools

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
    FakeSSLTools.buildContextAndTrust(keyStore)
  }

}

/**
 * An SSLEngineProvider which simply references the values in the
 * SelfSigned object.
 */
class SelfSignedSSLEngineProvider(serverConfig: ServerConfig, appProvider: ApplicationProvider) extends SSLEngineProvider {
  override def createSSLEngine: SSLEngine = SelfSigned.sslContext.createSSLEngine()
}
