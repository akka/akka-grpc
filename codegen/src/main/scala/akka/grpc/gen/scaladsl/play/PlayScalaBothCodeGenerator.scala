/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

class PlayScalaBothCodeGenerator extends PlayScalaServerCodeGenerator with PlayScalaClientCodeGenerator {
  override def name = "akka-grpc-play-scaladsl-both"
}
