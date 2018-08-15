# Details

## Client Lifecycle

Instances of the generated client may be long-lived and can be used concurrently.
You can keep the client running until your system terminates, or close it earlier. To
avoid leaking in the latter case, you should call `.close()` on the client.

When the connection breaks, the client will start failing requests and try reconnecting
to the server automatically.  If a connection can not be established after the configured number of attempts then
the client closes its self. When this happens the @scala[`Future`]@java[`CompletionStage`] 
returned by `closed()` will complete with a failure. You do not need to call `close()` in
this case. The default number of reconnection attempts is infinite.

If you're using a static name for your server then it will
be re-resolved between connection attempts so infinite is a sensible default. However
if another service discovery mechanism is used then set the reconnection attempts to
a small value i.e. 5 and re-create the client if the `closed()` @scala[`Future`]@java[`CompletionStage`]
is completed exceptionally. A `RestartingClient` utility is included that can wrap any
generated client and do this automatically. When the client is re-created service discovery
will be queried again. It is expected in a later version this will happen transparently.

Scala
:  @@snip [RestartingGreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/RestartingGreeterClient.scala) { #restarting-client }

Java
:  @@snip [RestartingGreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/RestartingGreeterClient.java) { #restarting-client }


To use the client use `withClient`. The actual client isn't exposed as it should not be stored
any where as it can be replaced when a failure happens.

Scala
:  @@snip [RestartingGreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/RestartingGreeterClient.scala) { #usage }

Java
:  @@snip [RestartingGreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/RestartingGreeterClient.java) { #usage }


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


