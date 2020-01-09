/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.google.protobuf.Descriptors.FileDescriptor;

import akka.annotation.ApiMayChange

@ApiMayChange
class ServiceObjectImpl(val name: String, val descriptor: FileDescriptor) extends ServiceObject
