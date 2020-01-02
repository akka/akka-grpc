/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.Optional

import akka.annotation.InternalApi
import akka.grpc.javadsl
import akka.grpc.scaladsl.{ BytesEntry, MetadataEntry, StringEntry }
import akka.util.ByteString
import io.grpc.Metadata

import scala.compat.java8.OptionConverters._

@InternalApi private[akka] object MetadataImpl {
  val empty = new MetadataImpl(List.empty)

  // somewhat expensive, but we try to avoid, multi-value entries are in reversed order
  private def metadataMapFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): Map[String, List[MetadataEntry]] = {
    val iterator = mutableMetadata.keys().iterator()
    var entries = Map.empty[String, List[MetadataEntry]]
    while (iterator.hasNext) {
      val key = iterator.next()
      val entry =
        if (key.endsWith("-bin")) {
          val bytes = mutableMetadata.get(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER))
          BytesEntry(ByteString(bytes))
        } else {
          val text = mutableMetadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
          StringEntry(text)
        }
      if (entries.contains(key)) {
        entries += (key -> (entry :: entries(key)))
      } else
        entries += (key -> (entry :: Nil))
    }
    entries
  }

  def scalaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): akka.grpc.scaladsl.Metadata = {
    val metadata = metadataMapFromGoogleGrpcMetadata(mutableMetadata)
    new akka.grpc.scaladsl.Metadata {
      def getText(key: String): Option[String] = metadata.get(key) match {
        case Some(StringEntry(text) :: Nil) => Some(text)
        case Some(multiple)                 => multiple.reverseIterator.collectFirst { case StringEntry(text) => text }
        case _                              => None
      }
      def getBinary(key: String): Option[ByteString] = metadata.get(key) match {
        case Some(BytesEntry(bytes) :: Nil) => Some(bytes)
        case Some(multiple)                 => multiple.reverseIterator.collectFirst { case BytesEntry(bytes) => bytes }
        case _                              => None
      }

      def asMap: Map[String, List[MetadataEntry]] = metadata
      override def toString: String = s"Metadata(${niceStringRep(metadata)})"
    }
  }

  def javaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): akka.grpc.javadsl.Metadata = {
    val metadata = metadataMapFromGoogleGrpcMetadata(mutableMetadata)
    new javadsl.Metadata {
      def getText(key: String): Optional[String] = metadata.get(key) match {
        case Some(StringEntry(text) :: Nil) => Optional.of(text)
        case Some(multiple)                 => multiple.reverseIterator.collectFirst { case StringEntry(text) => text }.asJava
        case _                              => Optional.empty()
      }
      def getBinary(key: String): Optional[ByteString] = metadata.get(key) match {
        case Some(BytesEntry(bytes) :: Nil) => Optional.of(bytes)
        case Some(multiple)                 => multiple.reverseIterator.collectFirst { case BytesEntry(bytes) => bytes }.asJava
        case _                              => Optional.empty()
      }
      override def toString: String = s"Metadata(${niceStringRep(metadata)})"
    }
  }

  private def niceStringRep(metadata: Map[String, List[MetadataEntry]]) =
    metadata.map { case (key, values) => key + " -> " + values.mkString("[", ", ", "]") }.mkString(", ")
}

@InternalApi private[akka] final class MetadataImpl(entries: List[(String, MetadataEntry)]) {
  def addEntry(key: String, value: String): MetadataImpl = {
    if (key.endsWith("-bin")) throw new IllegalArgumentException("String header names must not end with '-bin'")
    new MetadataImpl((key -> StringEntry(value)) :: entries)
  }

  def addEntry(key: String, value: ByteString): MetadataImpl = {
    if (!key.endsWith("-bin")) throw new IllegalArgumentException("Binary headers names must end with '-bin'")
    new MetadataImpl((key -> BytesEntry(value)) :: entries)
  }

  def toGoogleGrpcMetadata(): io.grpc.Metadata = {
    val mutableMetadata = new io.grpc.Metadata()
    entries.reverseIterator.foreach {
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
