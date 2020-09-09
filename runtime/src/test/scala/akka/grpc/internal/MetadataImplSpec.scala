/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.scaladsl.{ BytesEntry, Metadata, StringEntry }
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object MetadataImplSpec {
  val TEXT_ENTRIES = List(("key-a", "value-a"), ("key-c", "value-c"), ("key-b", "value-b"))

  val BINARY_ENTRIES = List(
    ("key-a-bin", ByteString.fromInts(10, 20, 30, 40)),
    ("key-c-bin", ByteString.fromInts(11, 21, 31, 41)),
    ("key-b-bin", ByteString.fromInts(12, 22, 32, 42)))

  val DUPE_TEXT_KEY = "key-dupe"
  val DUPE_TEXT_VALUES = List("a", "c", "b")

  val DUPE_BINARY_KEY = "key-dupe-bin"
  val DUPE_BINARY_VALUES = List(ByteString.fromInts(1), ByteString.fromInts(3), ByteString.fromInts(2))

  val NONEXISTENT_TEXT_KEY = "key-none"
  val NONEXISTENT_BINARY_KEY = "key-none-bin"
}

class MetadataImplSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import MetadataImplSpec._

  "EntryMetadataImpl" should {
    val entries = TEXT_ENTRIES.collect {
      case (k, v) => (k, StringEntry(v))
    } ++ BINARY_ENTRIES.collect {
      case (k, v) => (k, BytesEntry(v))
    } ++ DUPE_TEXT_VALUES.map { v => (DUPE_TEXT_KEY, StringEntry(v)) } ++ DUPE_BINARY_VALUES.map { v =>
      (DUPE_BINARY_KEY, BytesEntry(v))
    }

    testMetadata(new EntryMetadataImpl(entries))
  }

  "GrpcMetadataImpl" should {
    import io.grpc.Metadata

    // Set up the source gRPC metadata.
    val g = new io.grpc.Metadata()
    TEXT_ENTRIES.foreach {
      case (k, v) => g.put(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER), v)
    }
    BINARY_ENTRIES.foreach {
      case (k, v) => g.put(Metadata.Key.of(k, Metadata.BINARY_BYTE_MARSHALLER), v.toArray)
    }
    DUPE_TEXT_VALUES.foreach(v => g.put(Metadata.Key.of(DUPE_TEXT_KEY, Metadata.ASCII_STRING_MARSHALLER), v))
    DUPE_BINARY_VALUES.foreach(v => g.put(Metadata.Key.of(DUPE_BINARY_KEY, Metadata.BINARY_BYTE_MARSHALLER), v.toArray))

    testMetadata(new GrpcMetadataImpl(g))
  }

  "HeaderMetadataImpl" should {
    val headers = TEXT_ENTRIES.collect {
      case (k, v) => RawHeader(k, v)
    } ++ BINARY_ENTRIES.collect {
      case (k, v) => RawHeader(k, MetadataImpl.encodeBinaryHeader(v))
    } ++ DUPE_TEXT_VALUES.map(v => RawHeader(DUPE_TEXT_KEY, v)) ++ DUPE_BINARY_VALUES.map(v =>
      RawHeader(DUPE_BINARY_KEY, MetadataImpl.encodeBinaryHeader(v)))

    testMetadata(new HeaderMetadataImpl(headers))
  }

  def testMetadata(m: Metadata): Unit = {
    "return expected text values" in {
      TEXT_ENTRIES.foreach {
        case (k, v) => m.getText(k) should be(Some(v))
      }
    }
    "return None for nonexistent text key" in {
      m.getText(NONEXISTENT_TEXT_KEY) shouldBe None
    }
    "return most recently added value for repeated text entries" in {
      m.getText(DUPE_TEXT_KEY) shouldBe Some(DUPE_TEXT_VALUES.last)
    }

    "return correct binary values" in {
      BINARY_ENTRIES.foreach {
        case (k, v) => m.getBinary(k) shouldBe Some(v)
      }
    }
    "return None for nonexistent binary key" in {
      m.getBinary(NONEXISTENT_BINARY_KEY) shouldBe None
    }
    "return most recently added value for repeated binary entries" in {
      m.getBinary(DUPE_BINARY_KEY) shouldBe Some(DUPE_BINARY_VALUES.last)
    }

    "return a list with repeated entries in correct order" in {
      val list = m.asList
      list.collect { case (k, v) if k == DUPE_TEXT_KEY => v } shouldEqual DUPE_TEXT_VALUES.map(StringEntry)
      list.collect { case (k, v) if k == DUPE_BINARY_KEY => v } shouldEqual DUPE_BINARY_VALUES.map(BytesEntry)
    }

    "return a map repeated entries in correct order" in {
      val map = m.asMap
      map(DUPE_TEXT_KEY) shouldEqual DUPE_TEXT_VALUES.map(StringEntry)
      map(DUPE_BINARY_KEY) shouldEqual DUPE_BINARY_VALUES.map(BytesEntry)
    }
  }

}
