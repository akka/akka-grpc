/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import org.scalatest.{ Matchers, WordSpec }

class ProtocSpec extends WordSpec with Matchers {

  "The protoc error messages" must {
    "be parsed into details" in {
      GenerateMojo.parseError("notifications.proto:12:1: Expected top-level statement (e.g. \"message\").") should
        ===(Left(GenerateMojo.ProtocError("notifications.proto", 12, 1, "Expected top-level statement (e.g. \"message\").")))

    }
    "be kept if not parseable" in {
      GenerateMojo.parseError("My hovercraft is full of eels") should ===(Right("My hovercraft is full of eels"))
    }
  }

}
