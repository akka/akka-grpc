/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.annotation.ApiMayChange

import com.google.protobuf.Descriptors.FileDescriptor;

@ApiMayChange
trait ServiceObject {
  def name: String
  def descriptor: FileDescriptor
}
