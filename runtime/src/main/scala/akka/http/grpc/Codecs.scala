package akka.http.grpc

import scala.collection.immutable

object Codecs {
  // TODO should this list be made user-extensible?
  val supportedCodecs = immutable.Seq(Gzip)
}
