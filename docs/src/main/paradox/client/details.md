# Details

## Client Lifecycle

Instances of the generated client may be long-lived and can be used concurrently.
You can keep the client running until your system terminates, or close it earlier. To
avoid leaking in the latter case, you should call `.close()` on the client.

When the connection breaks, the client will try reconnecting to the server automatically.  On each reconnection 
attempt, If a connection the `ServiceDiscovery` will be used and a new host may be found.

When using client-side @ref[load balancing](details.md#load-balancing) the reconnection loop will run indefinitely.

When using a direct client (not load balanced) when the connection breaks you can set up a maximum number 
of reconnection attempts.  If that limit is reached, the client will shutdown.  The default number of attempts to 
reconnect is infinite and configurable via `GrpcClientSettings`'s `connectionAttempts`.

The client offers a method `closed()` that returns a @scala[`Future`]@java[`CompletionStage`] 
that will complete once the client is explicitly closed after invoking `close()`.  The returned @scala[`Future`]@java[`CompletionStage`]
will complete with a failure when the maximum number of `connectionAttempts` (which causes a shutdown). 

## Load balancing

When multiple endpoints are discovered for a gRPC client, currently one is
selected and used for all outgoing calls.

This approach, while naïve, in fact works well in many cases: when there
are multiple nodes available to handle requests, a server-side load balancer
is better-positioned to make decisions than any single client, as it can
take into account information from multiple clients, and sometimes even
lifecycle information (e.g. not forward requests to nodes that are scheduled
to shut down).

Client-side load balancing is desirable when you are using the default
`static` or the `grpc-dns` discovery mechanism. You can set the
`load-balancing-policy` client configuration option to `round_robin` to enable
the round_robin client-side load balancing strategy provided by grpc-java.

Note that load balancing is marked as [experimental](https://github.com/grpc/grpc-java/issues/1771) in grpc-java.

Client-side load balancing for other discovery mechanisms is
[not yet supported](https://github.com/akka/akka-grpc/issues/809).

## Request Metadata

Default request metadata, for example for authentication, can be provided through the
@apidoc[GrpcClientSettings] passed to the client when it is created, it will be the base metadata used for each request.

In some cases you will want to provide specific metadata to a single request, this can be done through the "lifted"
client API, each method of the service has an empty parameter list version (`.sayHello()`) on the client returning a @apidoc[SingleResponseRequestBuilder] or @apidoc[StreamResponseRequestBuilder].

After adding the required metadata the request is done by calling `invoke` with the request parameters.

Notice: method `addHeader` return a new object, you should use it like `String` or use it in the chain structure.

Scala
:  @@snip [GreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LiftedGreeterClient.scala) { #with-metadata }

Java
:  @@snip [GreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LiftedGreeterClient.java) { #with-metadata }

## Rich error model

Beyond status codes you can also use the [Rich error model](https://www.grpc.io/docs/guides/error/#richer-error-model). Currently there is no native support for this concept in Akka gRPC. However you can use the following manual approach.

Add the following dependency to receive required classes (that are based on the [common protobuf](https://cloud.google.com/apis/design/errors#error_model)):

`sbt`
:   @@@vars
```sbt
libraryDependencies += "io.grpc" % "grpc-protobuf" % "$grpc.version$"
```
@@@

`gradle`
:   @@@vars
```gradle
dependencies {
     implementation 'io.grpc:grpc-protobuf:$grpc.version$'
}
```
@@@

`maven`
:   @@@vars
```maven
<dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-protobuf</artifactId>
      <version>$grpc.version$</version>
</dependency>
```
@@@

Extract the `StatusRuntimeException` and parse the Rich error model to access `code`, `message` and `details`.

Scala
:  @@snip [GreeterClient.scala](/interop-tests/src/test/scala/akka/grpc/scaladsl/RichErrorModelSpec.scala) { #client_request }

Java
:  @@snip[RichErrorModelSpec](/interop-tests/src/test/java/example/myapp/helloworld/grpc/RichErrorModelTest.java) { #client_request }

 Please look @ref[here](../server/details.md) how to create errors with such details on the server side. 
