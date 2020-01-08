/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.google.protobuf.Descriptors.FileDescriptor;

trait ServiceObject {
  def name: String
  def descriptor: FileDescriptor
}
