/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.google.protobuf.Descriptors.FileDescriptor;

import akka.annotation.InternalApi

import akka.grpc.ServiceDescription

/**
 * INTERNAL API
 */
@InternalApi
class ServiceDescriptionImpl(val name: String, val descriptor: FileDescriptor) extends ServiceDescription
