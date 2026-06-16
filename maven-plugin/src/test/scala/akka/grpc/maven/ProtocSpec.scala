/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtocSpec extends AnyWordSpec with Matchers {
  "The protoc error messages" must {
    "be parsed into details" in {
      AbstractGenerateMojo.parseError(
        "notifications.proto:12:1: Expected top-level statement (e.g. \"message\").") should
      ===(
        Left(AbstractGenerateMojo
          .ProtocError("notifications.proto", 12, 1, "Expected top-level statement (e.g. \"message\").")))
    }
    "be kept if not parseable" in {
      AbstractGenerateMojo.parseError("My hovercraft is full of eels") should ===(
        Right("My hovercraft is full of eels"))
    }
  }

  import scala.jdk.CollectionConverters._

  "Parsing generator settings" should {
    "filter out the false values" in {
      val settings = Map("1" -> "true", "2" -> "false", "3" -> "False", "4" -> "")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("1", "4")
    }

    "convert camelCase into snake_case of keys" in {
      val settings = Map("flatPackage" -> "true", "serverPowerApis" -> "true")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("flat_package", "server_power_apis")
    }
  }

  "Selecting the protoc runner" should {
    "use the local protoc when an executable is set" in {
      AbstractGenerateMojo.useLocalProtoc("/usr/bin/protoc") shouldBe true
    }

    "fall back to the downloaded protoc when not set" in {
      AbstractGenerateMojo.useLocalProtoc(null) shouldBe false
      AbstractGenerateMojo.useLocalProtoc("") shouldBe false
      AbstractGenerateMojo.useLocalProtoc("   ") shouldBe false
    }
  }

  "Running a local protoc" should {
    "execute the given binary and return its exit code" in {
      // 'true' and 'false' are standard POSIX utilities returning 0 and 1 respectively
      AbstractGenerateMojo.runLocalProtoc("true", Seq.empty) shouldBe 0
      AbstractGenerateMojo.runLocalProtoc("false", Seq.empty) shouldBe 1
    }
  }

  "Extracting the protobuf release train" should {
    "read protobuf-java style versions" in {
      AbstractGenerateMojo.protocTrainOf("3.25.8") shouldBe Some(25)
      AbstractGenerateMojo.protocTrainOf("3.21.0") shouldBe Some(21)
    }
    "read the protoc-jar '-v' prefixed version" in {
      AbstractGenerateMojo.protocTrainOf("-v3.25.8") shouldBe Some(25)
    }
    "read 'protoc --version' output" in {
      AbstractGenerateMojo.protocTrainOf("libprotoc 25.8") shouldBe Some(25)
      AbstractGenerateMojo.protocTrainOf("libprotoc 29.0") shouldBe Some(29)
    }
    "treat the 3.<train> and <train> schemes as the same train" in {
      AbstractGenerateMojo.protocTrainOf("3.25.8") shouldBe AbstractGenerateMojo.protocTrainOf("libprotoc 25.8")
    }
    "return None when there is no version" in {
      AbstractGenerateMojo.protocTrainOf("libprotoc") shouldBe None
      AbstractGenerateMojo.protocTrainOf(null) shouldBe None
    }
  }

  "Displaying a version" should {
    "drop the protoc-jar '-v' prefix" in {
      AbstractGenerateMojo.displayVersion("-v3.25.8") shouldBe "3.25.8"
    }
    "leave a bare version unchanged" in {
      AbstractGenerateMojo.displayVersion("3.25.8") shouldBe "3.25.8"
    }
  }
}
