package akka.grpc

import io.grpc.CallOptions

// TODO document properties
final class GrpcClientSettings(
  val host: String,
  val port: Int,
  val overrideAuthority: Option[String],
  // TODO remove CallOptions here and build them ourselves inside the client
  val options: Option[CallOptions],
  // TODO more 'akka-http-like' way of configuring TLS
  val certificate: Option[String]) {

  def withOverrideAuthority(authority: String): GrpcClientSettings =
    new GrpcClientSettings(host, port, Some(authority), options, certificate)
  def withOptions(options: CallOptions): GrpcClientSettings =
    new GrpcClientSettings(host, port, overrideAuthority, Some(options), certificate)
  def withCertificate(certificate: String): GrpcClientSettings =
    new GrpcClientSettings(host, port, overrideAuthority, options, Some(certificate))

}

object GrpcClientSettings {
  /**
   * Scala API
   */
  def apply(host: String, port: Int): GrpcClientSettings =
    new GrpcClientSettings(host, port, None, None, None)

  /**
   * Scala API
   */
  def apply(host: String, port: Int, overrideAuthority: Option[String], options: Option[CallOptions], certificate: Option[String]): GrpcClientSettings =
    new GrpcClientSettings(host, port, overrideAuthority, options, certificate)

  /**
   * Java API
   */
  def create(host: String, port: Int): GrpcClientSettings = apply(host, port)
}
