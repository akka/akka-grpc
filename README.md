# akka-grpc

Support for building streaming gRPC servers and clients on top
of Akka Streams.

This library is meant to be used as a building block in projects using the Akka
toolkit. A play-grpc module to make this easy to use in a web application based
on the Play framework is in the works.

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
* interop-tests: test interoperability between the Akka implementation and the implementation from `io.gpc:grpc-interop-testing`, based on [gRPC's original testset](https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md). These tests are duplicated as more faithful 'scripted' tests under the sbt-project module.

Additionally, 'plugin-tester-java' and 'plugin-tester-scala' contain an example
project in Java and Scala respectively, with both sbt and Gradle configurations.
