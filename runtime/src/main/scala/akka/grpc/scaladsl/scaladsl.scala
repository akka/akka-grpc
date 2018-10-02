package akka.grpc

import java.util.Base64

import akka.http.scaladsl.model.HttpHeader
import akka.util.ByteString

import scala.collection.immutable

package object scaladsl {
  type MetadataMap = immutable.Map[String, Seq[MetadataEntry]]

  object MetadataMap {
    def apply(headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): MetadataMap = {
      // REVIEWER NOTE: modeled after akka.grpc.internal.MetadataImpl.metadataMapFromGoogleGrpcMetadata
      var entries = Map.empty[String, List[MetadataEntry]]
      headers.foreach { header =>
        val key = header.name()
        val entry =
          if (key.endsWith("-bin")) {
            val bytes = Base64.getDecoder.decode(header.value())
            BytesEntry(ByteString(bytes))
          } else {
            val text = header.value
            StringEntry(text)
          }
        if (entries.contains(key)) {
          entries += (key -> (entry :: entries(key)))
        } else
          entries += (key -> (entry :: Nil))
      }
      entries
    }
  }
}
