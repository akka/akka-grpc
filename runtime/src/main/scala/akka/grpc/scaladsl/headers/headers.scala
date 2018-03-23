package akka.grpc.scaladsl.headers

import akka.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }

import scala.util.Try

final class `Message-Encoding`(encoding: String) extends ModeledCustomHeader[`Message-Encoding`] {
  override def renderInRequests = true
  override def renderInResponses = true
  override val companion = `Message-Encoding`
  override def value: String = encoding
}
object `Message-Encoding` extends ModeledCustomHeaderCompanion[`Message-Encoding`] {
  override val name = "grpc-encoding"
  override def parse(encoding: String) = Try(new `Message-Encoding`(encoding))
}