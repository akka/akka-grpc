# Overview

## gRPC

[gRPC](https://grpc.io) is a transport mechanism for request/response and (non-persistent) streaming use cases. See
@ref[Why gRPC?](whygrpc.md) for more information about when to use gRPC as your transport.

## Akka gRPC

Akka gRPC provides support for building streaming gRPC servers and clients on top
of Akka Streams.

It features:

 * A generator, that starts from a protobuf service definitions, for:
    - Model classes
    - The service API as a @scala[Scala trait]@java[Java interface] using Akka Stream `Source`s
    - On the @ref[server side](server/index.md), code to create an Akka HTTP route based on your implementation of the service
    - On the @ref[client side](client/index.md) side, a client for the service
 * gRPC Runtime implementation that uses Akka HTTP/2 support for the server side and grpc-netty-shaded for the client side. 
 * Support for @ref[sbt](buildtools/sbt.md), @ref[gradle](buildtools/gradle.md), and @ref[Maven](buildtools/maven.md),
   and the @ref[Play Framework](play-framework.md).  

## Project Status

This library is in preview mode: basic functionality is in place, but APIs and
build plugins are still expected to be improved.

The API on both sides (Client and Server) is a simple Akka Streams-based one.
The client has a 'power user' API, which also planned for the server (see [#179](https://github.com/akka/akka-grpc/issues/179)).

The client side is
currently implemented on top of [io.grpc:grpc-netty-shaded](https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded),
we plan to replace this by [io.grpc:grpc-core](https://mvnrepository.com/artifact/io.grpc/grpc-core) and Akka HTTP.

As for performance, we are currently relying on the JVM TLS implementation,
which is sufficient for many use cases, but is planned to be replaced with
[conscrypt](https://github.com/google/conscrypt) or [netty-tcnative](https://netty.io/wiki/forked-tomcat-native.html).
