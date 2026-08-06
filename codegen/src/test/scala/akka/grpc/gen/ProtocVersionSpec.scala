/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtocVersionSpec extends AnyWordSpec with Matchers {

  "Extracting the protobuf release train" should {
    "read protobuf-java style versions" in {
      ProtocVersion.trainOf("3.25.8") shouldBe Some(25)
      ProtocVersion.trainOf("3.21.0") shouldBe Some(21)
      ProtocVersion.trainOf("4.35.0") shouldBe Some(35)
      ProtocVersion.trainOf("4.26.1") shouldBe Some(26)
    }

    "read the '-v' prefixed version the protocVersion setting uses" in {
      ProtocVersion.trainOf("-v3.25.8") shouldBe Some(25)
    }

    "read 'protoc --version' output" in {
      ProtocVersion.trainOf("libprotoc 25.8") shouldBe Some(25)
      ProtocVersion.trainOf("libprotoc 29.0") shouldBe Some(29)
      ProtocVersion.trainOf("libprotoc 35.0") shouldBe Some(35)
      ProtocVersion.trainOf("libprotoc 3.19.4") shouldBe Some(19)
    }

    "treat the <major>.<train> and <train> schemes as the same train" in {
      ProtocVersion.trainOf("3.25.8") shouldBe ProtocVersion.trainOf("libprotoc 25.8")
      ProtocVersion.trainOf("4.35.0") shouldBe ProtocVersion.trainOf("libprotoc 35.0")
    }

    "return None when there is no version" in {
      ProtocVersion.trainOf("libprotoc") shouldBe None
      ProtocVersion.trainOf(null) shouldBe None
    }
  }

  "Displaying a version" should {
    "drop the '-v' prefix" in {
      ProtocVersion.display("-v3.25.8") shouldBe "3.25.8"
    }

    "leave a bare version unchanged" in {
      ProtocVersion.display("3.25.8") shouldBe "3.25.8"
    }
  }

  "Checking alignment" should {
    "be aligned within the same release train" in {
      ProtocVersion.checkAlignment("protoc", "-v3.25.8", "libprotoc 25.1") shouldBe ProtocVersion.Alignment.Aligned
      ProtocVersion.checkAlignment("protoc", "4.35.0", "libprotoc 35.0") shouldBe ProtocVersion.Alignment.Aligned
    }

    "be misaligned across release trains" in {
      ProtocVersion.checkAlignment("protoc", "-v3.25.8", "libprotoc 29.0") match {
        case ProtocVersion.Alignment.Misaligned(message) =>
          message should include("release 29.x")
          message should include("3.25.8")
        case other => fail(s"expected Misaligned, got $other")
      }
    }

    "be misaligned for a protoc predating the protobuf 4.x versioning scheme" in {
      // protoc reported `3.<train>.<patch>` up to train 20 and `<train>.<patch>` from 21 on
      ProtocVersion.checkAlignment("protoc", "4.35.0", "libprotoc 3.19.4") match {
        case ProtocVersion.Alignment.Misaligned(message) => message should include("release 19.x")
        case other                                       => fail(s"expected Misaligned, got $other")
      }
      ProtocVersion.checkAlignment("protoc", "4.35.0", "libprotoc 21.12") match {
        case ProtocVersion.Alignment.Misaligned(message) => message should include("release 21.x")
        case other                                       => fail(s"expected Misaligned, got $other")
      }
    }

    "be undetermined when the reported version cannot be parsed" in {
      ProtocVersion.checkAlignment("protoc", "-v3.25.8", "libprotoc") shouldBe a[ProtocVersion.Alignment.Undetermined]
    }
  }

  "Querying the version" should {
    "throw when the executable cannot be run" in {
      a[RuntimeException] should be thrownBy ProtocVersion.queryVersion("akka-grpc-no-such-protoc-binary")
    }

    "report that a path-like executable does not exist" in {
      val thrown = the[RuntimeException] thrownBy ProtocVersion.queryVersion("/no/such/path/protoc")
      thrown.getMessage should include("does not exist")
    }
  }

  "Verifying once" should {
    "propagate the failure when the executable cannot be run" in {
      var warned = false
      val thrown =
        the[RuntimeException] thrownBy ProtocVersion.verify("/no/such/path/protoc", "-v3.25.8", _ => warned = true)
      thrown.getMessage should include("does not exist")

      warned shouldBe false
    }
  }
}
