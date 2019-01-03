/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl

object ScalaBothCodeGenerator extends ScalaServerCodeGenerator with ScalaClientCodeGenerator {
  override def name = "akka-grpc-scaladsl-both"
}
