# Details

## Client Lifecycle

Instances of the generated client may be long-lived and can be used concurrently.
You can keep the client running until your system terminates, or close it earlier. To
avoid leaking in the latter case, you should call `.close()` on the client.

When the connection breaks, the client will start failing requests and try reconnecting
to the server automatically.  If a connection can not be established after the configured number of attempts then
the client will try to use the `ServiceDiscovery` implementation to connect to a different instance. This mechanism separates the physical connection from the logical one and gives an extra layer of flexibility to support both client-side and server-side balancing. The default number of attempts to reconnect to the same host and port is infinite and configurable via `GrpcClientSettings`'s `connectionAttempts`. The number of times a client will reuse the `ServiceDiscovery` instance to locate a new remote instance is infinite.

The client offers a method `closed()` that returns a @scala[`Future`]@java[`CompletionStage`] 
that will complete once the client is explicitly closed after invoking `close()`.

If you're using a static name for your server (or a Service Discovery with hard-corded values) then the server will
be re-resolved between connection attempts and infinite is a sensible default value for `GrpcClientSettings#connectionAttempts`. However,
if you setup another service discovery mechanism (e.g. a service discovery based on DNS-SRV in Kubernetes) then the reconnection attempts should be set to
a small value (i.e. 5) so the client can recreate the connection to a different server instance when the connection is dropped and can't be restablished. 

## Request Metadata

Default request metadata, for example for authentication, can be provided through the
`GrpcClientSettings` passed to the client when it is created, it will be the base metadata used for each request.

In some cases you will want to provide specific metadata to a single request, this can be done through the "lifted"
client API, each method of the service has an empty parameter list version on the client returning a `RequestBuilder`.

After adding the required metadata the request is done by calling `invoke` with the request parameters.

Scala
:  @@snip [GreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LiftedGreeterClient.scala) { #with-metadata }

Java
:  @@snip [GreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LiftedGreeterClient.java) { #with-metadata }


