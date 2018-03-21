# akka-grpc

Support for building streaming gRPC servers and clients on top
of Akka Streams.

This library is meant to be used as a building block in projects using the Akka
toolkit.

## Status

This library is in preview mode: basic functionality is in place, but API's and
build system plugins are still expected to be improved. The client side is
currently implemented on top of [io.grpc:grpc-netty-shaded](https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded),
we plan to replace this by [io.grpc:grpc-core](https://mvnrepository.com/artifact/io.grpc/grpc-core) and Akka HTTP.

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
* interop-tests: test interoperability between the Akka implementation and the implementation from `io.gpc:grpc-interop-testing`, based on [gRPC's original testset](https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md). These tests are duplicated as more faithful 'scripted' tests under the sbt-project module.

Additionally, 'plugin-tester-java' and 'plugin-tester-scala' contain an example
project in Java and Scala respectively, with both sbt and Gradle configurations.

## Running the tests

gRPC runs on HTTP/2 and all connections use HTTPS. Tests in this repo use the ALPN agent to create the ALPN/NPN connection. 
The agent is included in all required configurations when running tests inside `sbt` but that's not the case when running tests inside your IDE. 
If you want to run tests outside `sbt` setup your launcher to include the following JVM arguments:

```
-javaagent:/home/username/.ivy2/cache/org.mortbay.jetty.alpn/jetty-alpn-agent/jars/jetty-alpn-agent-2.0.7.jar
``` 

You will have to review the value of the path and replace `/home/username/.ivy2/cache/` with the actual location of your `.ivy2` cache repository (usually that's located on your `$HOME` folder) 