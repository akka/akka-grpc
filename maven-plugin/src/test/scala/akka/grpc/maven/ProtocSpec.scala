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
}
