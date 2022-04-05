/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

/**
 * Thrown if close() is called on a client that uses a shared channel.
 */
final class GrpcClientCloseException()
    extends IllegalStateException("Client close() should not be called when using a shared channel")
