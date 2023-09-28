# akka-grpc

Support for building streaming gRPC servers and clients on top
of Akka Streams.

This library is meant to be used as a building block in projects using the Akka
toolkit.

## Documentation

- [Akka gRPC reference](https://doc.akka.io/docs/akka-grpc/current/) documentation 

## Project Status

This library is ready to be used in production, but API's and build system plugins are still expected to be improved and [may change](https://doc.akka.io/docs/akka/current/common/may-change.html).

The API on both sides (Client and Server) is a simple Akka Streams-based one.

The client side is
currently implemented on top of [io.grpc:grpc-netty-shaded](https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded),
we plan to replace this by just [io.grpc:grpc-core](https://mvnrepository.com/artifact/io.grpc/grpc-core) and [Akka HTTP](https://doc.akka.io/docs/akka-http/current).

As for performance, we are currently relying on the JVM TLS implementation,
which is sufficient for many use cases, but is planned to be replaced with
[conscrypt](https://github.com/google/conscrypt) or [netty-tcnative](https://netty.io/wiki/forked-tomcat-native.html).

## General overview

gRPC is a schema-first RPC framework, where your protocol is declared in a
protobuf definition, and requests and responses will be streamed over an HTTP/2
connection.

Based on a protobuf service definition, akka-grpc can generate:

* Model classes (using plain protoc for Java or scalapb for Scala)
* The API (as an interface for Java or a trait for Scala), expressed in Akka Streams `Source`s
* On the server side, code to create an Akka HTTP route based on your implementation of the API
* On the client side, a client for the API.

## Project structure

The project is split up in a number of subprojects:

* codegen: code generation shared among plugins
* runtime: run-time utilities used by the generated code
* sbt-plugin: the sbt plugin
* scalapb-protoc-plugin: the scalapb Scala model code generation packaged as a protoc plugin, to be used from gradle
* [interop-tests](interop-tests/README.md)

Additionally, 'plugin-tester-java' and 'plugin-tester-scala' contain an example
project in Java and Scala respectively, with both sbt and Gradle configurations.

## Compatibility & support

Support for Akka gRPC is available via the [Lightbend Subscription](https://lightbend.com/lightbend-subscription)

## License

Akka gRPC is licensed under the Business Source License 1.1, see LICENSE.
