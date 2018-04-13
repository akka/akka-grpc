package akka.grpc


import io.grpc.CallOptions

// TODO remove default parameters here and create companion object with suitable apply methods
class GrpcClientSettings(
  val host: String,
  val port: Int,
  val overrideAuthority: Option[String] = None,
  // TODO remove CallOptions here and build them ourselves inside the client
  val options: Option[CallOptions] = None,
  // TODO more 'akka-http-like' way of configuring TLS
  val certificate: Option[String] = None,
) {
}
