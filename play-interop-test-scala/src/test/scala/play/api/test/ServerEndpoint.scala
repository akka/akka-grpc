/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import javax.net.ssl.{ SSLContext, X509TrustManager }

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
  ssl: Option[ServerEndpoint.ClientSsl]) {

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