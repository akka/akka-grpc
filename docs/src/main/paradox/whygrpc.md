# Why gRPC?

[gRPC](https://grpc.io) is a transport mechanism for request/response
and (non-persistent) streaming use cases.

It is a schema-first RPC framework, where your protocol is declared in a
@ref[protobuf service descriptor](proto.md), and requests and responses will be streamed over an HTTP/2
connection.

It has several advantages:

 * Schema-first design favors well-defined and decoupled service interfaces over brittle ad-hoc solutions.
 * Protobuf-based wire protocol is efficient, well-known, and allows compatible schema evolution.
 * Based on HTTP/2 which allows multiplexing several data streams over a single connection.
 * Streaming requests and responses are first class.
 * There are tools available for many languages allowing seamless interoperability between clients and services written
   in different languages.

That makes it well-suited for:

 * Connections between internal services
 * Connecting to external services that expose a gRPC API (even ones written in other languages)
 * Serving data to web or mobile front-ends

## gRPC vs REST

* Where REST is more flexible about encoding, gRPC standardizes on Protobuf.
* Where REST can be either schemaless or use a 3rd-party schema, gRPC always declares the service and messages in a Protobuf schema definition.

## gRPC vs SOAP

* Where SOAP is more flexible about transport, gRPC standardizes on HTTP/2.
* Where in SOAP protocols are often set in stone once defined (often requiring a new path for every version of the service), Protobuf is explicitly intended to support schema evolution.

## gRPC vs Message Bus

* While built on an efficient non-blocking implementation, gRPC is still 'synchronous' in the sense that it requires both 'sides' of the communication to be available at the same time. When using a (persistent) message bus only the producer and the bus must be up, the consumer does not need to be available, leading to a higher degree of decoupling.
* While gRPC supports bidirectional streaming for each request, when using a message bus the streams are decoupled

## gRPC vs Akka Remoting

* While Akka Remoting allows exchanging messages between Akka ActorSystems transparently, it still requires significant effort to support efficient and compatible message serialization.
  Large messages can clog the message transport. In contrast to gRPC, streaming is not first-class but needs to be built on top of message passing (e.g. by using [StreamRefs](https://doc.akka.io/docs/akka/current/stream/stream-refs.html)).
* Akka Remoting's wire protocol might change with Akka versions and configuration, so you need to make sure that all parts of your system run similar enough versions. gRPC on the other
  hand guarantees longer-term stability of the protocol, so gRPC clients and services are more loosely coupled.
* Where message passing as with Akka Remoting is fire-and-forget which decouples service execution, any kind of RPC requires waiting until the remote procedure call is answered.
  Waiting (even non-blocking) for a response which is needed for any case of RPC often binds significant resources. To be fair, (Akka) actor communication is often structured in a
  request/response fashion which makes it very similar to more traditional RPC techniques and gives the same disadvantages
  (like state that needs to be kept on the "client" side requiring timeouts while waiting for a response).
