/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.Optional

import akka.annotation.InternalApi
import akka.grpc.{ javadsl, scaladsl }
import akka.util.ByteString
import io.grpc.Metadata

// the io.grpc.Metadata class is mutable and has a horrible API, let's hide it
@InternalApi private[akka] sealed trait MetadataEntry
@InternalApi private[akka] case class StringEntry(value: String) extends MetadataEntry
@InternalApi private[akka] case class BytesEntry(value: ByteString) extends MetadataEntry

@InternalApi private[akka] object MetadataImpl {
  val empty = new MetadataImpl(Map.empty)

  private def metadataMapFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): Map[String, MetadataEntry] = {
    val iterator = mutableMetadata.keys().iterator()
    var entries = Map.canBuildFrom[String, MetadataEntry]()
    while (iterator.hasNext) {
      val key = iterator.next()
      if (key.endsWith("-bin")) {
        val bytes = mutableMetadata.get(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER))
        entries += (key -> BytesEntry(ByteString(bytes)))
      } else {
        val text = mutableMetadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
        entries += (key -> StringEntry(text))
      }
    }
    entries.result()
  }

  def scalaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): akka.grpc.scaladsl.Metadata = {
    val metadata = metadataMapFromGoogleGrpcMetadata(mutableMetadata)
    new akka.grpc.scaladsl.Metadata {
      def getText(key: String): Option[String] = metadata.get(key) match {
        case Some(s: StringEntry) => Some(s.value)
        case _ => None
      }
      def getBinary(key: String): Option[ByteString] = metadata.get(key) match {
        case Some(b: BytesEntry) => Some(b.value)
        case _ => None
      }
      override def toString: String = s"Metadata(${niceStringRep(metadata)})"
    }
  }

  def javaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): akka.grpc.javadsl.Metadata = {
    val metadata = metadataMapFromGoogleGrpcMetadata(mutableMetadata)
    new javadsl.Metadata {
      def getText(key: String): Optional[String] = metadata.get(key) match {
        case Some(s: StringEntry) => Optional.of(s.value)
        case _ => Optional.empty()
      }
      def getBinary(key: String): Optional[ByteString] = metadata.get(key) match {
        case Some(b: BytesEntry) => Optional.of(b.value)
        case _ => Optional.empty()
      }
      override def toString: String = s"Metadata(${niceStringRep(metadata)})"
    }
  }

  private def niceStringRep(metadata: Map[String, MetadataEntry]) =
    metadata.map { case (key, value) => key + " -> " + value }.mkString(", ")
}

@InternalApi private[akka] final class MetadataImpl(entries: Map[String, MetadataEntry]) {

  def withEntry(key: String, value: String): MetadataImpl = {
    if (key.endsWith("-bin")) throw new IllegalArgumentException("String header names must not end with '-bin'")
    new MetadataImpl(entries + (key -> StringEntry(value)))
  }

  def withEntry(key: String, value: ByteString): MetadataImpl = {
    if (!key.endsWith("-bin")) throw new IllegalArgumentException("Binary headers names must end with '-bin'")
    new MetadataImpl(entries + (key -> BytesEntry(value)))
  }

  def toGoogleGrpcMetadata(): io.grpc.Metadata = {
    val mutableMetadata = new io.grpc.Metadata()
    entries.foreach {
      case (key, entry) =>
        entry match {
          case StringEntry(value) =>
            mutableMetadata.put(io.grpc.Metadata.Key.of(key, io.grpc.Metadata.ASCII_STRING_MARSHALLER), value)

          case BytesEntry(value) =>
            mutableMetadata.put(io.grpc.Metadata.Key.of(key, io.grpc.Metadata.BINARY_BYTE_MARSHALLER), value.toArray)
        }
    }
    mutableMetadata
  }

}
