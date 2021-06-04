/*
 * Copyright (C) 2020-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.grpc.ProtobufSerializer
import akka.util.ByteString
import java.io.{ ByteArrayInputStream, InputStream }
import java.lang.Character
import io.grpc.KnownLength
import scala.util.Random
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object BaseMarshallerSpec {
  object StringProtobufSerializer extends ProtobufSerializer[String] {
    override final def serialize(t: String): ByteString = ByteString.fromString(t, "UTF-8")
    override final def deserialize(bytes: ByteString): String = bytes.utf8String
  }

  object StringKnownLengthProtoMarshaller extends BaseMarshaller[String](StringProtobufSerializer) {
    override final def parse(stream: InputStream): String = super.parse(stream)
    override final def stream(value: String): InputStream =
      new ByteArrayInputStream(value.getBytes("UTF-8")) with KnownLength
  }

  object StringProtoMarshaller extends BaseMarshaller[String](StringProtobufSerializer) {
    override final def parse(stream: InputStream): String = super.parse(stream)
    override final def stream(value: String): InputStream =
      new ByteArrayInputStream(value.getBytes("UTF-8"))
  }

  def randomString(size: Int): String =
    Iterator.continually(Random.nextInt(32 * 1024).toChar).filter(Character.isLetterOrDigit).take(size).mkString
}

class BaseMarshallerSpec extends AnyWordSpecLike with Matchers {
  import BaseMarshallerSpec._
  "BaseMarshaller" should {
    "correctly parse empty input" in {
      StringProtoMarshaller.parse(StringProtoMarshaller.stream("")) shouldBe ""
      StringKnownLengthProtoMarshaller.parse(StringKnownLengthProtoMarshaller.stream("")) shouldBe ""
    }

    "correctly parse InputStream of KnownLength for a bunch of random strings" in {
      (1 to 100).foreach { _ =>
        val testString = randomString(Random.nextInt(64 * 1024))
        StringKnownLengthProtoMarshaller.parse(StringKnownLengthProtoMarshaller.stream(testString)) shouldBe testString
      }
    }

    "correctly parse InputStream for a bunch of random strings" in {
      (1 to 100).foreach { _ =>
        val testString = randomString(Random.nextInt(64 * 1024))
        StringProtoMarshaller.parse(StringProtoMarshaller.stream(testString)) shouldBe testString
      }
    }
  }
}
