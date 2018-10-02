package akka.grpc.scaladsl

import akka.util.ByteString

sealed trait MetadataEntry
case class StringEntry(value: String) extends MetadataEntry
case class BytesEntry(value: ByteString) extends MetadataEntry
