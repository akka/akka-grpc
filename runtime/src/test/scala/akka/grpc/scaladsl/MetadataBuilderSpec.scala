/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetadataBuilderSpec extends AnyWordSpec with Matchers {
  import akka.grpc.internal.MetadataImplSpec._

  "MetadataBuilder" should {
    "return empty metadata" in {
      MetadataBuilder.empty.asList shouldBe empty
      MetadataBuilder.empty.asMap shouldBe empty
    }
    "handle distinct text entries" in {
      val b = new MetadataBuilder
      TEXT_ENTRIES.foreach {
        case (k, v) => b.addText(k, v)
      }
      val m = b.build()

      TEXT_ENTRIES.foreach {
        case (k, v) => m.getText(k) shouldBe Some(v)
      }
    }

    "handle repeated text entries" in {
      val b = new MetadataBuilder
      DUPE_TEXT_VALUES.foreach { v => b.addText(DUPE_TEXT_KEY, v) }
      val m = b.build()

      m.getText(DUPE_TEXT_KEY) shouldBe Some(DUPE_TEXT_VALUES.last)

      val dupeEntries = DUPE_TEXT_VALUES.map(StringEntry)
      m.asMap(DUPE_TEXT_KEY) shouldBe dupeEntries
      m.asList.collect {
        case (k, e) if k == DUPE_TEXT_KEY => e
      } shouldBe dupeEntries
    }

    "throw exception for '-bin' suffix on text key" in {
      an[IllegalArgumentException] should be thrownBy (new MetadataBuilder).addText("foo-bin", "x")
    }

    "throw exception for missing '-bin' suffix on binary key" in {
      an[IllegalArgumentException] should be thrownBy (new MetadataBuilder).addBinary("foo", ByteString.empty)
    }

    "handle distinct binary entries" in {
      val b = new MetadataBuilder
      BINARY_ENTRIES.foreach {
        case (k, v) => b.addBinary(k, v)
      }
      val m = b.build()

      BINARY_ENTRIES.foreach {
        case (k, v) => m.getBinary(k) shouldBe Some(v)
      }
    }

    "handle repeated binary entries" in {
      val b = new MetadataBuilder
      DUPE_BINARY_VALUES.foreach { v => b.addBinary(DUPE_BINARY_KEY, v) }
      val m = b.build()

      m.getBinary(DUPE_BINARY_KEY) shouldBe Some(DUPE_BINARY_VALUES.last)

      val dupeEntries = DUPE_BINARY_VALUES.map(BytesEntry)
      m.asMap(DUPE_BINARY_KEY) shouldBe dupeEntries
      m.asList.collect {
        case (k, e) if k == DUPE_BINARY_KEY => e
      } shouldBe dupeEntries
    }
  }

}
