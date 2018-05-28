/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.javadsl

import java.util.Optional

import akka.util.ByteString

trait Metadata {
  def getText(key: String): Optional[String]
  def getBinary(key: String): Optional[ByteString]
}
