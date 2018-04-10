# akka-grpc

Support for building streaming gRPC servers and clients on top
of Akka Streams.

This library is meant to be used as a building block in projects using the Akka
toolkit.

## General overview

gRPC is a schema-first RPC framework, where your protocol is declared in a
protobuf definition, and requests and responses will be streamed over an HTTP/2
connection.

Based on a protobuf service definition, akka-grpc can generate:

* Model classes (using plain protoc for Java or scalapb for Scala)
* The API @scala[trait]@java[interface], expressed in Akka Streams `Source`s
* On the server side, code to create an Akka HTTP route based on your implementation of the API
* On the client side, a client for the API.



@@@ index

* [server](server.md)
* [client](client.md)
* [proto](proto.md)
* [sbt](sbt.md)
* [maven](maven.md)
* [gradle](gradle.md)

@@@
