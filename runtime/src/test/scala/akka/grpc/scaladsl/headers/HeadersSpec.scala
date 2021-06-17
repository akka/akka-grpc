package akka.grpc.scaladsl.headers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

class HeadersSpec extends AnyWordSpec with Matchers {
  "Status-Message.value()" should {
    "use percent-encoding" in {
      // test cases taken from https://github.com/grpc/grpc-java/blob/79e75bace40cea7e4be72e7dcd1f41c3ad6ee857/api/src/test/java/io/grpc/StatusTest.java#L65
      val inAndExpectedOut = Table(
        ("raw input", "expected encoded value"),
        ("my favorite character is i", "my favorite character is i"),
        ("my favorite character is \n", "my favorite character is %0A"),
        ("my favorite character is \u0000", "my favorite character is %00"),
        ("my favorite character is %", "my favorite character is %25"),
        ("my favorite character is 𐀁", "my favorite character is %F0%90%80%81"),
        // \ud801 is a high surrogate, a lone surrogate character is getting decoded as ? with UTF-8
        ("my favorite character is \ud801", "my favorite character is ?"),
        // \udc37 is a low surrogate, a lone surrogate character is getting decoded as ? with UTF-8
        ("my favorite character is \udc37", "my favorite character is ?"),
        // a pair of surrogate characters is fine
        ("my favorite character is " + 0xdbff.toChar + 0xdfff.toChar, "my favorite character is %F4%8F%BF%BF"))

      forAll(inAndExpectedOut) { (in, expected) =>
        new `Status-Message`(in).value() should equal(expected)
      }
    }
  }

  "Status-Message.parse()" should {
    "should decode percent-encoded values" in {
      // test cases taken from https://github.com/grpc/grpc-java/blob/79e75bace40cea7e4be72e7dcd1f41c3ad6ee857/api/src/test/java/io/grpc/StatusTest.java#L65
      val inAndExpectedOut = Table(
        ("raw input", "expected decoded value"),
        (Array[Byte]('H', 'e', 'l', 'l', 'o'), "Hello"),
        (Array[Byte]('H', '%', '6', '1', 'o'), "Hao"),
        (Array[Byte]('H', '%', '0', 'A', 'o'), "H\no"),
        (Array[Byte]('%', 'F', '0', '%', '9', '0', '%', '8', '0', '%', '8', '1'), "𐀁"),
        (Array[Byte]('a', 'b', 'c', '%', 'C', '5', '%', '8', '2'), "abcł"))

      forAll(inAndExpectedOut) { (in, expected) =>
        val actual = `Status-Message`.parse(new String(in))
        actual.get.unencodedValue should equal(expected)
      }
    }

    "should decode as is in case two chars following percent cannot be decoded as hex" in {
      val inAndExpectedOut = Table(
        ("raw input", "expected decoded value"),
        (Array[Byte]('%', 'G'), "%G"),
        (Array[Byte]('%', 'G', '0'), "%G0"),
        (Array[Byte]('%', 'G', '0', '%', ',', '0'), "%G0%,0"),
        (Array[Byte]('%', '%', '0', '%', '%'), "%%0%%"))

      forAll(inAndExpectedOut) { (in, expected) =>
        val actual = `Status-Message`.parse(new String(in))
        actual.get.unencodedValue should equal(expected)
      }
    }
  }

  "Status-Message.value() and Status-Message.parse()" should {
    "roundtrip for UTF-8 encodable sequence of bytes" in {
      val examples = Table(
        "examples",
        "example%",
        "yet another%%example",
        "abc\ndef",
        "abc\ndef\n",
        "abc\r\ndef\r\n",
        "abc\r\n\r\ndef\r\n",
        "Καλημέρα κόσμε",
        "コンニチハ",
        "zażółć gęślą jaźń")

      examples.foreach { in =>
        val encoded = new `Status-Message`(in).value()
        val decoded = `Status-Message`.parse(encoded).get.unencodedValue
        decoded should equal(in)
      }
    }
  }
}
