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
be re-resolved between connection attempts and infinite is a sensible default value for @apidoc[GrpcClientSettings.connectionAttempts](GrpcClientSettings). However,
if you setup another service discovery mechanism (e.g. a service discovery based on DNS-SRV in Kubernetes) then the reconnection attempts should be set to
a small value (i.e. 5) so the client can recreate the connection to a different server instance when the connection is dropped and can't be restablished. 

## Load balancing

When multiple endpoints are discovered for a gRPC client, currently one is
selected and used for all outgoing calls.

This approach, while naïve, in fact works well in many cases: when there
are multiple nodes available to handle requests, a server-side load balancer
is better-positioned to make decisions than any single client, as it can
take into account information from multiple clients, and sometimes even
lifecycle information (e.g. not forward requests to nodes that are scheduled
to shut down).

When client-side load balancing is desirable, when you are using the default
`static` or the `grpc-dns` discovery mechanism, you can set the
`grpc-load-balancing` client configuration option to `round-robin` to enable
the round-robin client-side load balancing strategy provided by grpc-java.

Client-side load balancing for other discovery mechanisms is
[not yet supported](https://github.com/akka/akka-grpc/issues/809).
