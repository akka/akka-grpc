# Akka HTTP interop

Akka gRPC is built on top of [Akka HTTP](https://docs.akka.io/docs/akka-http).
This means it is possible to leverage the Akka HTTP API's to create more
complicated services, for example serving non-gRPC endpoints next to
gRPC endpoints or adding additional behavior around your gRPC routes.

## Example: authentication/authorization

One use case could be adding cross-cutting concerns such as
authentication/authorization. Suppose you have an API that
you want to secure using a token. You already have a regular
HTTP API that users can use to obtain a token, and want to
secure your gRPC routes to only accept calls that include this
token.

### Akka HTTP authentication route

This route could be any arbitrary Akka HTTP route. For this example
we just provide a hint in the response body:

Scala
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #http-route }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #http-route }

### Akka gRPC route

We create the Akka gRPC service implementation, and convert it to a @apidoc[Route$] as well:

Scala
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #grpc-route }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #grpc-route }

### Securing the Akka gRPC route

We can wrap the gRPC route just like any @apidoc[Route$], applying the authorization:

Scala
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #grpc-protected }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #grpc-protected }

### Tying it all together

Finally we can combine the routes and serve them. Remember we need to use `bindAndHandleAsync` to enable HTTP/2 support:

Scala
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #combined }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #combined }

### Future work

For more in-depth integration you might want to pass information from the
Akka HTTP route into your gRPC service implementation.

Currently, you could achieve this by adding the required information to your
service implementation constructor, and constructing a new Handler for each request.

In the future we plan to provide a nicer API for this, for example we could pass the
Akka HTTP attributes (introduced in 10.2.0) as Metadata when using the PowerApi.
