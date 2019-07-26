/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import org.scalatest.{ Matchers, WordSpec }

class ProtocSpec extends WordSpec with Matchers {

  "The protoc error messages" must {
    "be parsed into details" in {
      GenerateMojo.parseError("notifications.proto:12:1: Expected top-level statement (e.g. \"message\").") should
      ===(
        Left(
          GenerateMojo.ProtocError("notifications.proto", 12, 1, "Expected top-level statement (e.g. \"message\").")))

    }
    "be kept if not parseable" in {
      GenerateMojo.parseError("My hovercraft is full of eels") should ===(Right("My hovercraft is full of eels"))
    }
  }

  import scala.collection.JavaConverters._

  "Parsing generator settings" should {
    "filter out the false values" in {
      val settings = Map("1" -> "true", "2" -> "false", "3" -> "False", "4" -> "")
      GenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("1", "4")
    }

    "convert camelCase into snake_case of keys" in {
      val settings = Map("flatPackage" -> "true", "serverPowerApis" -> "true")
      GenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("flat_package", "server_power_apis")
    }
  }

}
