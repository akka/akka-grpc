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
    }

    "read the protoc-jar '-v' prefixed version" in {
      ProtocVersion.trainOf("-v3.25.8") shouldBe Some(25)
    }

    "read 'protoc --version' output" in {
      ProtocVersion.trainOf("libprotoc 25.8") shouldBe Some(25)
      ProtocVersion.trainOf("libprotoc 29.0") shouldBe Some(29)
    }

    "treat the 3.<train> and <train> schemes as the same train" in {
      ProtocVersion.trainOf("3.25.8") shouldBe ProtocVersion.trainOf("libprotoc 25.8")
    }

    "return None when there is no version" in {
      ProtocVersion.trainOf("libprotoc") shouldBe None
      ProtocVersion.trainOf(null) shouldBe None
    }
  }

  "Displaying a version" should {
    "drop the protoc-jar '-v' prefix" in {
      ProtocVersion.display("-v3.25.8") shouldBe "3.25.8"
    }

    "leave a bare version unchanged" in {
      ProtocVersion.display("3.25.8") shouldBe "3.25.8"
    }
  }

  "Checking alignment" should {
    "be aligned within the same release train" in {
      ProtocVersion.checkAlignment(
        "protoc",
        "-v3.25.8",
        Some("libprotoc 25.1")) shouldBe ProtocVersion.Alignment.Aligned
    }

    "be misaligned across release trains" in {
      ProtocVersion.checkAlignment("protoc", "-v3.25.8", Some("libprotoc 29.0")) match {
        case ProtocVersion.Alignment.Misaligned(message) =>
          message should include("protobuf 29.x")
          message should include("3.25.8")
        case other => fail(s"expected Misaligned, got $other")
      }
    }

    "be undetermined when the version cannot be queried" in {
      ProtocVersion.checkAlignment("protoc", "-v3.25.8", None) shouldBe a[ProtocVersion.Alignment.Undetermined]
    }

    "be undetermined when the reported version cannot be parsed" in {
      ProtocVersion
        .checkAlignment("protoc", "-v3.25.8", Some("libprotoc")) shouldBe a[ProtocVersion.Alignment.Undetermined]
    }
  }
}
