/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.util.{ Locale, Optional, List => jList, Map => jMap }
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import scala.jdk.OptionConverters._
import akka.annotation.InternalApi
import akka.annotation.InternalStableApi
import akka.http.scaladsl.model.{ AttributeKey, HttpHeader, HttpMessage }
import akka.http.javadsl.{ model => jm }
import akka.japi.Pair
import akka.util.ByteString
import akka.grpc.scaladsl.{ BytesEntry, Metadata, MetadataEntry, MetadataStatus, StringEntry }
import akka.grpc.javadsl
import com.google.protobuf.any
import com.google.rpc.Status
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion }

/**
 * INTERNAL API
 */
// Note: empty value used by generated code, cannot be private
@InternalApi object MetadataImpl {
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

/**
 * INTERNAL API
 */
// Note: type used by generated code, cannot be private
@InternalApi final class MetadataImpl(val entries: List[(String, MetadataEntry)]) {
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
 *
 * @param delegate The underlying mutable metadata.
 */
@InternalApi
class GrpcMetadataImpl(delegate: io.grpc.Metadata) extends Metadata {
  require(delegate != null, "Metadata delegate must be present")
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

  override def attribute[T](key: AttributeKey[T]): Option[T] = None

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
      delegate.getAll(textKey(key)).asScala.map(StringEntry.apply).toList
    }
}

/**
 * This class represents metadata as a list of (key, entry) tuples.
 *
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

  override def attribute[T](key: AttributeKey[T]): Option[T] = None
}

/**
 * This class wraps a list of headers from an HttpResponse with the Metadata interface.
 *
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

  override def attribute[T](key: AttributeKey[T]): Option[T] = None

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
 * This class wraps an HttpMessage with the Metadata interface.
 *
 * @param message The HTTP message to wrap.
 */
class HttpMessageMetadataImpl(message: HttpMessage) extends HeaderMetadataImpl(message.headers) {
  override def attribute[T](key: AttributeKey[T]): Option[T] = message.attribute(key)
  override private[grpc] val rawHttpMessage = Some(message)
}

/**
 * This class wraps a scaladsl.Metadata instance with the javadsl.Metadata interface.
 *
 * @param delegate The underlying Scala metadata instance.
 */
@InternalApi
class JavaMetadataImpl @InternalStableApi() (val delegate: Metadata)
    extends javadsl.Metadata
    with javadsl.MetadataStatus {
  override def getText(key: String): Optional[String] =
    delegate.getText(key).toJava

  override def getBinary(key: String): Optional[ByteString] =
    delegate.getBinary(key).toJava

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

  override def getAttribute[T](key: jm.AttributeKey[T]): Optional[T] =
    delegate.rawHttpMessage.flatMap(_.attribute(key)).toJava

  override def toString: String =
    delegate.toString

  private def richDelegate =
    delegate match {
      case r: MetadataStatus => r
      case other             => throw new IllegalArgumentException(s"Delegate metadata is not RichMetadata but ${other.getClass}")
    }

  override def getStatus(): Status = richDelegate.status

  override def getCode(): Int = richDelegate.code

  override def getMessage(): String = richDelegate.message

  private lazy val javaDetails: jList[com.google.protobuf.any.Any] = richDelegate.details.asJava
  def getDetails(): jList[com.google.protobuf.any.Any] = javaDetails

  def getParsedDetails[K <: GeneratedMessage](companion: GeneratedMessageCompanion[K]): jList[K] =
    richDelegate.getParsedDetails(companion).asJava

  def getParsedDetails[K <: com.google.protobuf.GeneratedMessageV3](messageType: K): jList[K] = {
    val parser = messageType.getParserForType
    val messageTypeUrl = s"type.googleapis.com/${messageType.getDescriptorForType.getFullName}"
    richDelegate.details.collect {
      case scalaPbAny if scalaPbAny.typeUrl == messageTypeUrl =>
        parser.parseFrom(scalaPbAny.value).asInstanceOf[K]
    }.asJava
  }
}

class RichGrpcMetadataImpl(delegate: io.grpc.Status, meta: io.grpc.Metadata)
    extends GrpcMetadataImpl(meta)
    with MetadataStatus {
  override val raw: Option[io.grpc.Metadata] = Some(meta)
  override lazy val status: com.google.rpc.Status =
    io.grpc.protobuf.StatusProto.fromStatusAndTrailers(delegate, meta)

  override def code: Int = status.getCode
  override def message: String = status.getMessage

  override lazy val details: Seq[any.Any] = status.getDetailsList.asScala.map { item =>
    fromJavaProto(item)
  }.toVector

  def getParsedDetails[K <: scalapb.GeneratedMessage](
      implicit companion: scalapb.GeneratedMessageCompanion[K]): Seq[K] = {
    val typeUrl = "type.googleapis.com/" + companion.scalaDescriptor.fullName
    details.filter(_.typeUrl == typeUrl).map(_.unpack)
  }

  private def fromJavaProto(javaPbSource: com.google.protobuf.Any): com.google.protobuf.any.Any =
    com.google.protobuf.any.Any(typeUrl = javaPbSource.getTypeUrl, value = javaPbSource.getValue)
}
