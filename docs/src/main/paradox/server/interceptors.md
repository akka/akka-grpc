# Interceptors

A server interceptor runs logic around every call to a gRPC service, before the request is unmarshalled.
Use it for cross-cutting concerns such as authentication, logging or metrics.

The interceptor gets the service name, the method name, the @apidoc[HttpRequest] and a `next` function
that continues with the rest of the chain and the service implementation. It can:

 * Reject the call by returning a failed @scala[`Future`]@java[`CompletionStage`], for example with a
   @apidoc[GrpcServiceException]. The failure is turned into a gRPC error response for the client.
 * Pass information to the service implementation by adding an attribute to the request
   before calling `next`. A @ref[Power API](details.md#accessing-request-metadata) implementation reads it from the @apidoc[Metadata]
   with @scala[`metadata.attribute(key)`]@java[`metadata.getAttribute(key)`].
 * Observe the outcome by transforming the response returned by `next`.

Interceptors do not see the unmarshalled request or response messages. Errors in a streamed response happen
after the response has completed and are not visible to the interceptor.

## Defining an interceptor

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #interceptor }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #interceptor }

## Applying interceptors to one service

Wrap the handler of the service with the interceptors. The wrapped handler can be combined with other
handlers just like the generated handler. Here the greeter service requires a token but server reflection does not:

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #grpc-protected }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #grpc-protected }

@@@ div { .group-java }

The Java variant takes the `ServiceDescription` of the service so that calls to other services pass through
untouched and can be handled by the other handlers in `ServiceHandler.concatOrNotFound`.

@@@

## Applying interceptors to all services

Wrap the combined handler instead. Every call reaching it passes through the interceptors:

Scala
:   @@snip [ServerInterceptorSpec.scala](/interop-tests/src/test/scala/akka/grpc/scaladsl/ServerInterceptorSpec.scala) { #all-services }

Java
:   @@snip [ServerInterceptorTest.java](/interop-tests/src/test/java/akka/grpc/javadsl/ServerInterceptorTest.java) { #all-services }

Several interceptors can be given to one call. The first one is the outermost, so it sees the request first
and the response last.
