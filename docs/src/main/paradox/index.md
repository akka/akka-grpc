# Akka gRPC

[gRPC](https://grpc.io) is a transport mechanism for request/response
and (non-persistent) streaming use cases. Use it for:

* connections between internal services
* connecting to external services that expose a gRPC API (even ones written in other languages)
* serving data to web or mobile front-ends

This library provides support for building streaming gRPC servers and clients on top
of Akka Streams.

## General overview

gRPC is a schema-first RPC framework, where your protocol is declared in a
protobuf definition, and requests and responses will be streamed over an HTTP/2
connection.

Based on a protobuf service definition, akka-grpc can generate:

* Model classes (using plain protoc for Java or scalapb for Scala)
* The API @scala[trait]@java[interface], expressed in Akka Streams `Source`s
* On the server side, code to create an Akka HTTP route based on your implementation of the API
* On the client side, a client for the API.

### gRPC vs REST

* Where REST is more flexible about encoding, gRPC standardizes on Protobuf.
* Where REST can be either schemaless or use a 3rd-party schema, gRPC always declares the service and messages in a Protobuf schema definition.

### gRPC vs SOAP

* Where SOAP is more flexible about transport, gRPC standardizes on HTTP/2.
* Where in SOAP protocols are often set in stone once defined (often requiring a new path for every version of the service), Protobuf is explicitly intended to support schema evolution.

### gRPC vs Message Bus

* While built on an efficient non-blocking implementation, gRPC is still 'synchonous' in the sense that it requires both 'sides' of the communication to be available at the same time. When using a (persistent) message bus only the producer and the bus must be up, the consumer does not need to be available, leading to a higher degree of decoupling.
* While gRPC supports bidirectional streaming for each request, when using a message bus the streams are decoupled

## Project Status

This library is in preview mode: basic functionality is in place, but API's and
build system plugins are still expected to be improved.

The API on both sides (Client and Server) is a simple Akka Streams-based one.
We plan to also provide a 'power user' API for each of these ([#191](https://github.com/akka/akka-grpc/issues/191), [#179](https://github.com/akka/akka-grpc/issues/179)).

The client side is
currently implemented on top of [io.grpc:grpc-netty-shaded](https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded),
we plan to replace this by [io.grpc:grpc-core](https://mvnrepository.com/artifact/io.grpc/grpc-core) and Akka HTTP.

As for performance, we are currently relying on the JVM TLS implementation,
which is sufficient for many use cases, but is planned to be replaced with
[conscrypt](https://github.com/google/conscrypt) or [netty-tcnative](https://netty.io/wiki/forked-tomcat-native.html).

@@@ index

* [apidesign](apidesign.md)
* [server](server.md)
* [client](client.md)
* [proto](proto.md)
* [sbt](sbt.md)
* [maven](maven.md)
* [gradle](gradle.md)

@@@
