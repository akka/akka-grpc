# Akka HTTP interop

Akka gRPC is built on top of @extref[Akka HTTP](akka-http:).
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
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #http-route }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #http-route }

### Akka gRPC route

We create the Akka gRPC service implementation, and convert it to a @apidoc[Route$] as well:

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #grpc-route }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #grpc-route }

### Securing the Akka gRPC route

We can wrap the gRPC route just like any @apidoc[Route$], applying the authorization:

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #grpc-protected }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #grpc-protected }

### Tying it all together

Finally we can combine the routes and serve them. Remember we need to use `bindAndHandleAsync` to enable HTTP/2 support:

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/AuthenticatedGreeterServer.scala) { #combined }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/AuthenticatedGreeterServer.java) { #combined }


## Example: logging, error handling, and passing request context

This example shows how we can wrap a gRPC service implementation into a @apidoc[Route$] to get the following common server features:

1. Log the HTTP request and response corresponding to each RPC.
2. Pass the @apidoc[RequestContext] into the RPC handler. 
3. If the RPC fails, apply a custom error handler and log the error.

### Implementation

We start with an implementation of the sayHello RPC that does some primitive validation on the name.  
If the name starts with a lowercase later, we return an `IllegalArgumentException`.
Otherwise, we return a standard response.

However, there's also a slight bug in the implementation.
If the name is empty, the call to get the first character in the first if statement will throw an Exception.

Scala
:  @@snip [LoggingErrorHandlingGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.scala) { #implementation }

Java
:  @@snip [LoggingErrorHandlingGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.java) { #implementation }

### Method to log, handle, and recover each RPC 

We define a method that returns a @apidoc[Route$] implementing our logging and error recovery features.
The method takes three parameters.

1. A function that takes a @apidoc[RequestContext] and returns a service implementation. This gives us the opportunity to use the context in the implementation. If we don't need it, we can just ignore the context and return a fixed implementation.
2. A function that takes an @apidoc[ActorSystem] and returns a partial function from Throwable to gRPC @apidoc[Trailers].
3. A function that takes the service implementation and an error handler and returns a request handler (a function from @apidoc[HttpRequest] to a @scala[`Future`]@java[`CompletionStage`] of @apidoc[HttpResponse]). 

The method first uses an existing directive to log requests and results.
Then it wraps the given error handler into an error handler that also logs the error.
Finally, it calls the given functions to handle incoming requests.

Scala
:  @@snip [LoggingErrorHandlingGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.scala) { #method }

Java
:  @@snip [LoggingErrorHandlingGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.java) { #method }

### Custom error mapping

We define a partial function to handle the custom exception we defined above.

Scala
:  @@snip [LoggingErrorHandlingGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.scala) { #custom-error-mapping }

Java
:  @@snip [LoggingErrorHandlingGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.java) { #custom-error-mapping }

### Tying it all together

Finally, we invoke the new method and bind the resulting @apidoc[Route$].

Scala
:  @@snip [LoggingErrorHandlingGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.scala) { #combined }

Java
:  @@snip [LoggingErrorHandlingGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/LoggingErrorHandlingGreeterServer.java) { #combined }

### Results

We make three calls: one with a valid name, one with a lowercase name, and one with an empty name.

For the valid name, the RPC succeeds and the server prints only the response log:

```
[INFO] [05/15/2022 09:24:36.850] [Server-akka.actor.default-dispatcher-8] [akka.actor.ActorSystemImpl(Server)] loggingErrorHandlingGrpcRoute: Response for
  Request : HttpRequest(HttpMethod(POST),http://127.0.0.1/helloworld.GreeterService/SayHello,Vector(TE: trailers, User-Agent: grpc-java-netty/1.45.1, grpc-accept-encoding: gzip),HttpEntity.Chunked(application/grpc),HttpProtocol(HTTP/2.0))
  Response: Complete(HttpResponse(200 OK,List(grpc-encoding: gzip),HttpEntity.Strict(application/grpc+proto,42 bytes total),HttpProtocol(HTTP/1.1)))
```

For the lowercase name, the server prints the error log and the response log.
Note that the server still returns a status code 200, even though the RPC failed.
This is because gRPC encodes a failure as a successful HTTP response containing the error in the body.

```
[ERROR] [05/15/2022 09:24:36.902] [Server-akka.actor.default-dispatcher-5] [akka.actor.ActorSystemImpl(Server)] Grpc failure handled and mapped to akka.grpc.Trailers@4ab49ff7
java.lang.IllegalArgumentException: Name must be capitalized
	at example.myapp.helloworld.LoggingErrorHandlingGreeterServer$Impl$1.sayHello(LoggingErrorHandlingGreeterServer.scala:43)
	at example.myapp.helloworld.grpc.GreeterServiceHandler$.$anonfun$partial$2(GreeterServiceHandler.scala:118)
	at scala.concurrent.Future.$anonfun$flatMap$1(Future.scala:307)
	at scala.concurrent.impl.Promise.$anonfun$transformWith$1(Promise.scala:41)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:64)
	at akka.dispatch.BatchingExecutor$AbstractBatch.processBatch(BatchingExecutor.scala:56)
	at akka.dispatch.BatchingExecutor$BlockableBatch.$anonfun$run$1(BatchingExecutor.scala:93)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:23)
	at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:85)
	at akka.dispatch.BatchingExecutor$BlockableBatch.run(BatchingExecutor.scala:93)
	at akka.dispatch.TaskInvocation.run(AbstractDispatcher.scala:48)
	at akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask.exec(ForkJoinExecutorConfigurator.scala:48)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:183)

[INFO] [05/15/2022 09:24:36.905] [Server-akka.actor.default-dispatcher-5] [akka.actor.ActorSystemImpl(Server)] loggingErrorHandlingGrpcRoute: Response for
  Request : HttpRequest(HttpMethod(POST),http://127.0.0.1/helloworld.GreeterService/SayHello,Vector(TE: trailers, User-Agent: grpc-java-netty/1.45.1, grpc-accept-encoding: gzip),HttpEntity.Chunked(application/grpc),HttpProtocol(HTTP/2.0))
  Response: Complete(HttpResponse(200 OK,List(grpc-encoding: gzip),HttpEntity.Chunked(application/grpc+proto),HttpProtocol(HTTP/1.1)))
```

For the empty name, the server prints a slightly different error log and the response log, 

```
[ERROR] [05/15/2022 09:24:36.914] [Server-akka.actor.default-dispatcher-5] [akka.actor.ActorSystemImpl(Server)] Grpc failure UNHANDLED and mapped to akka.grpc.Trailers@5e1d9001
java.util.NoSuchElementException: next on empty iterator
	at scala.collection.Iterator$$anon$2.next(Iterator.scala:41)
	at scala.collection.Iterator$$anon$2.next(Iterator.scala:39)
	at scala.collection.IterableLike.head(IterableLike.scala:109)
	at scala.collection.IterableLike.head$(IterableLike.scala:108)
	at scala.collection.immutable.StringOps.scala$collection$IndexedSeqOptimized$$super$head(StringOps.scala:33)
	at scala.collection.IndexedSeqOptimized.head(IndexedSeqOptimized.scala:129)
	at scala.collection.IndexedSeqOptimized.head$(IndexedSeqOptimized.scala:129)
	at scala.collection.immutable.StringOps.head(StringOps.scala:33)
	at example.myapp.helloworld.LoggingErrorHandlingGreeterServer$Impl$1.sayHello(LoggingErrorHandlingGreeterServer.scala:42)
	at example.myapp.helloworld.grpc.GreeterServiceHandler$.$anonfun$partial$2(GreeterServiceHandler.scala:118)
	at scala.concurrent.Future.$anonfun$flatMap$1(Future.scala:307)
	at scala.concurrent.impl.Promise.$anonfun$transformWith$1(Promise.scala:41)
	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:64)
	at akka.dispatch.BatchingExecutor$AbstractBatch.processBatch(BatchingExecutor.scala:56)
	at akka.dispatch.BatchingExecutor$BlockableBatch.$anonfun$run$1(BatchingExecutor.scala:93)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:23)
	at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:85)
	at akka.dispatch.BatchingExecutor$BlockableBatch.run(BatchingExecutor.scala:93)
	at akka.dispatch.TaskInvocation.run(AbstractDispatcher.scala:48)
	at akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask.exec(ForkJoinExecutorConfigurator.scala:48)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:183)

[INFO] [05/15/2022 09:24:36.914] [Server-akka.actor.default-dispatcher-5] [akka.actor.ActorSystemImpl(Server)] loggingErrorHandlingGrpcRoute: Response for
  Request : HttpRequest(HttpMethod(POST),http://127.0.0.1/helloworld.GreeterService/SayHello,Vector(TE: trailers, User-Agent: grpc-java-netty/1.45.1, grpc-accept-encoding: gzip),HttpEntity.Chunked(application/grpc),HttpProtocol(HTTP/2.0))
  Response: Complete(HttpResponse(200 OK,List(grpc-encoding: gzip),HttpEntity.Chunked(application/grpc+proto),HttpProtocol(HTTP/1.1)))
```

## Future work

For in-depth akka-grpc/akka-http integration we currently need to pass information from the Akka HTTP route
into the service implementation constructor, and construct a new Handler for each request.
This pattern is shown in an example above.

In the future we plan to provide a nicer API for this, for example we could pass the
Akka HTTP attributes (introduced in 10.2.0) as Metadata when using the PowerApi.
