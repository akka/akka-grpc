/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.scaladsl

import akka.util.ByteString

trait Metadata {
  def getText(key: String): Option[String]
  def getBinary(key: String): Option[ByteString]
}
