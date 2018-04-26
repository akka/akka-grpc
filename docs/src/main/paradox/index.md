# Akka gRPC

[gRPC](https://grpc.io) is a transport mechanism for request/response
and non-persistent streaming use cases. Use it for:

* connections between internal services
* connecting to external services that expose a gRPC API, even ones written in other languages.
* connections with web or mobile front-ends

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

* Where REST is more flexible about encoding, gRPC standardizes on HTTP/2 and Protobuf.
* Where REST can be either schemaless or use a 3rd-party schema, gRPC always declares the service and messages in a Protobuf schema definition.

### gRPC vs SOAP

* Where SOAP is more flexible about transport, gRPC standardizes on HTTP/2 and Protobuf.
* Where in SOAP protocols are often set in stone once defined (often requiring a new path for every version of the service), Protobuf is explicitly intended to support schema evolution.

### gRPC vs Message Bus

* Where gRPC requires both 'sides' of the communication to be available at the same time, when using a message bus only the producer and the bus must be up, the consumer does not need to be available.
* Where gRPC supports streaming in either direction, when using a message bus these streams are decoupled

@@@ index

* [apidesign](apidesign.md)
* [server](server.md)
* [client](client.md)
* [proto](proto.md)
* [sbt](sbt.md)
* [maven](maven.md)
* [gradle](gradle.md)

@@@
