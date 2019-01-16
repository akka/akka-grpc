/**
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.scaladsl.Service
import org.scalatest.{ Matchers, WordSpec }

class PlayScalaClientCodeGeneratorSpec extends WordSpec with Matchers {

  "The PlayScalaClientCodeGenerator" must {

    "choose the single package name" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(Service("a.b", "MyService", "???", Nil))) should ===("a.b")
    }

    "choose the longest common package name" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(
          Service("a.b.c", "MyService", "???", Nil),
          Service("a.b.e", "OtherService", "???", Nil))) should ===("a.b")
    }

    "choose the root package if no common packages" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(
          Service("a.b.c", "MyService", "???", Nil),
          Service("c.d.e", "OtherService", "???", Nil))) should ===("")
    }
  }

}
