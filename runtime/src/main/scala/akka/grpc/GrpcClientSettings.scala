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
  val trustedCaCertificate: Option[String]) {

  def withOverrideAuthority(authority: String): GrpcClientSettings =
    new GrpcClientSettings(host, port, Some(authority), options, trustedCaCertificate)
  def withOptions(options: CallOptions): GrpcClientSettings =
    new GrpcClientSettings(host, port, overrideAuthority, Some(options), trustedCaCertificate)
  def withTrustedCaCertificate(certificate: String): GrpcClientSettings =
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
  def apply(host: String, port: Int, overrideAuthority: Option[String], options: Option[CallOptions], trustedCaCertificate: Option[String]): GrpcClientSettings =
    new GrpcClientSettings(host, port, overrideAuthority, options, trustedCaCertificate)

  /**
   * Java API
   */
  def create(host: String, port: Int): GrpcClientSettings = apply(host, port)
}
