/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.{ Locale, Optional, List => jList, Map => jMap }

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.japi.Pair
import akka.util.ByteString
import akka.grpc.scaladsl.{ BytesEntry, Metadata, MetadataEntry, StringEntry }
import akka.grpc.javadsl

@InternalApi private[akka] object MetadataImpl {
  val BINARY_SUFFIX: String = io.grpc.Metadata.BINARY_HEADER_SUFFIX

  val empty = new MetadataImpl(List.empty)

  def scalaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): Metadata =
    new GrpcMetadataImpl(mutableMetadata)

  def javaMetadataFromGoogleGrpcMetadata(mutableMetadata: io.grpc.Metadata): javadsl.Metadata =
    new JavaMetadataImpl(scalaMetadataFromGoogleGrpcMetadata(mutableMetadata))

  def encodeBinaryHeader(bytes: ByteString): String = bytes.encodeBase64.utf8String

  def decodeBinaryHeader(value: String): ByteString = ByteString(value).decodeBase64

  def toMap(list: List[(String, MetadataEntry)]): Map[String, List[MetadataEntry]] = {
    // This method is complicated by the changes to mapValues in scala 2.13.

    // For Scala 2.12, this should be:
    // list.groupBy(_._1).mapValues(_.map(_._2)).toMap

    // For scala 2.13, Map.mapValues is deprecated. The suggested migration is:
    // list.groupBy(_._1).view.mapValues(_.map(_._2)).toMap

    // Even better would be:
    // list.groupMap(_._1)(_._2)

    // For now, drop back to map() to deal with the incompatibility between versions.
    list.groupBy(_._1).map(t => (t._1, t._2.map(_._2))).toMap
  }

  def niceStringRep(metadata: Map[String, List[MetadataEntry]]) = {
    val data = metadata.map { case (key, values) => key + " -> " + values.mkString("[", ", ", "]") }.mkString(", ")
    s"Metadata($data)"
  }
}

@InternalApi private[akka] final class MetadataImpl(val entries: List[(String, MetadataEntry)]) {
  def addEntry(key: String, value: String): MetadataImpl = {
    if (key.endsWith(MetadataImpl.BINARY_SUFFIX))
      throw new IllegalArgumentException(s"String header names must not end with '${MetadataImpl.BINARY_SUFFIX}'")
    new MetadataImpl((key -> StringEntry(value)) :: entries)
  }

  def addEntry(key: String, value: ByteString): MetadataImpl = {
    if (!key.endsWith(MetadataImpl.BINARY_SUFFIX))
      throw new IllegalArgumentException(s"Binary headers names must end with '${MetadataImpl.BINARY_SUFFIX}'")
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

/**
 * This class wraps a mutable Metadata from io.grpc with the Scala Metadata interface.
 * @param delegate The underlying mutable metadata.
 */
@InternalApi
class GrpcMetadataImpl(delegate: io.grpc.Metadata) extends Metadata {
  private lazy val map = delegate.keys.iterator.asScala.map(key => key -> getEntries(key)).toMap

  override def getText(key: String): Option[String] =
    Option(delegate.get(textKey(key)))

  override def getBinary(key: String): Option[ByteString] =
    Option(delegate.get(binaryKey(key))).map(ByteString.fromArray)

  override def asMap: Map[String, List[MetadataEntry]] =
    map

  override def asList: List[(String, MetadataEntry)] = {
    delegate.keys.iterator.asScala.flatMap(key => getEntries(key).map(entry => (key, entry))).toList
  }

  override def toString: String =
    MetadataImpl.niceStringRep(map)

  private def binaryKey(key: String): io.grpc.Metadata.Key[Array[Byte]] =
    io.grpc.Metadata.Key.of(key, io.grpc.Metadata.BINARY_BYTE_MARSHALLER)

  private def textKey(key: String): io.grpc.Metadata.Key[String] =
    io.grpc.Metadata.Key.of(key, io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  private def getEntries(key: String): List[MetadataEntry] =
    if (key.endsWith(io.grpc.Metadata.BINARY_HEADER_SUFFIX)) {
      delegate.getAll(binaryKey(key)).asScala.map(b => BytesEntry(ByteString.fromArray(b))).toList
    } else {
      delegate.getAll(textKey(key)).asScala.map(StringEntry).toList
    }
}

/**
 * This class represents metadata as a list of (key, entry) tuples.
 * @param entries The list of (key, entry) tuples.
 */
@InternalApi
class EntryMetadataImpl(entries: List[(String, MetadataEntry)] = Nil) extends Metadata {
  override def getText(key: String): Option[String] =
    entries.reverseIterator.collectFirst {
      case (name, StringEntry(value)) if name == key => value
    }

  override def getBinary(key: String): Option[ByteString] =
    entries.reverseIterator.collectFirst {
      case (name, BytesEntry(value)) if name == key => value
    }

  override def asMap: Map[String, List[MetadataEntry]] =
    MetadataImpl.toMap(entries)

  override def asList: List[(String, MetadataEntry)] =
    entries

  override def toString: String =
    MetadataImpl.niceStringRep(asMap)
}

/**
 * This class wraps a list of headers from an HttpResponse with the Metadata interface.
 * @param headers The list of HTTP response headers.
 */
@InternalApi
class HeaderMetadataImpl(headers: immutable.Seq[HttpHeader] = immutable.Seq.empty) extends Metadata {
  private lazy val map: Map[String, List[MetadataEntry]] =
    MetadataImpl.toMap(asList)

  override def getText(key: String): Option[String] = {
    val lcKey = key.toLowerCase(Locale.ROOT)
    headers.reverseIterator.collectFirst {
      case header if header.is(lcKey) => header.value
    }
  }

  override def getBinary(key: String): Option[ByteString] = {
    val lcKey = key.toLowerCase(Locale.ROOT)
    headers.reverseIterator.collectFirst {
      case header if header.is(lcKey) =>
        MetadataImpl.decodeBinaryHeader(header.value)
    }
  }

  override def asMap: Map[String, List[MetadataEntry]] =
    map

  override def asList: List[(String, MetadataEntry)] =
    headers.map(toKeyEntry).toList

  override def toString: String =
    MetadataImpl.niceStringRep(asMap)

  private def toKeyEntry(header: HttpHeader): (String, MetadataEntry) = {
    val key = header.lowercaseName()
    val entry =
      if (key.endsWith(MetadataImpl.BINARY_SUFFIX)) {
        val bytes = MetadataImpl.decodeBinaryHeader(header.value)
        BytesEntry(bytes)
      } else {
        val text = header.value
        StringEntry(text)
      }
    (key, entry)
  }
}

/**
 * This class wraps a scaladsl.Metadata instance with the javadsl.Metadata interface.
 * @param delegate The underlying Scala metadata instance.
 */
@InternalApi
class JavaMetadataImpl(delegate: Metadata) extends javadsl.Metadata {
  override def getText(key: String): Optional[String] =
    delegate.getText(key).asJava

  override def getBinary(key: String): Optional[ByteString] =
    delegate.getBinary(key).asJava

  override def asMap(): jMap[String, jList[javadsl.MetadataEntry]] = {
    // This method is also affected by incompatible changes between scala 2.12 and 2.13. (See comment in
    // MetadataImp.toMap for more details.)

    // For now, as a workaround, implement the conversion in terms of map instead of mapValues.
    delegate.asMap.map(t => t._1 -> t._2.map(_.asInstanceOf[javadsl.MetadataEntry]).asJava).toMap.asJava
  }

  override def asList(): jList[Pair[String, javadsl.MetadataEntry]] =
    delegate.asList.map(t => Pair[String, javadsl.MetadataEntry](t._1, t._2)).asJava

  override def asScala: Metadata =
    delegate

  override def toString: String =
    delegate.toString
}
