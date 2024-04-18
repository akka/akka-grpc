## Streaming gRPC
 
In the @ref[first example](index.md) we saw a gRPC service call for single request reply. Let's add
a bidirectional streaming call. First we will run it and then look at how it's implemented.

### Running the streaming call

To run Hello World including the streaming calls:

1. Run the server:

    sbt
    :   ```
        sbt "runMain com.example.helloworld.GreeterServer"
        ```

    Maven
    :   ```
        mvn compile dependency:properties exec:exec@server
        ```

    Gradle
    :   ```
        ./gradlew runServer
        ```
 
    @sbt[sbt]@maven[Maven]@gradle[Gradle] builds the project and runs the gRPC server

    This starts the server in the same way as in the first example we ran previously. The output should include something like:
 
    ```
    gRPC server bound to: /127.0.0.1:8080
    ```

1. Run the client, open another console window and enter:

    sbt
    :   ```
        sbt "runMain com.example.helloworld.GreeterClient Alice"
        ```

    Maven
    :   ```
        mvn -DGreeterClient.user=Alice compile dependency:properties exec:exec@client
        ```

    Gradle
    :   ```
        ./gradlew runClient -PGreeterClient.user=Alice
        ```

    @sbt[sbt]@maven[Maven]@gradle[Gradle] runs the gRPC client for Alice

    Note that the difference from the first example is the additional argument
    @sbt[`Alice`]@maven[`-DGreeterClient.user=Alice`]@gradle[`-PGreeterClient.user=Alice`].

    The output should include something like:

    ```
    Performing request: Alice
    Performing streaming requests: Alice
    HelloReply(Hello, Alice)
    Alice got streaming reply: Hello, Alice-0
    Alice got streaming reply: Hello, Alice-1
    Alice got streaming reply: Hello, Alice-2
    Alice got streaming reply: Hello, Alice-3
    ```

    The "Performing request: Alice" and "HelloReply(Hello, Alice)" comes from the single request response call in the
    previous example and the "streaming" are new.

1. Open yet another console window and enter:

    sbt
    :   ```
        sbt "runMain com.example.helloworld.GreeterClient Bob"
        ```

    Maven
    :   ```
        mvn -DGreeterClient.user=Bob compile dependency:properties exec:exec@client
        ```

    Gradle
    :   ```
        ./gradlew runClient -PGreeterClient.user=Bob
        ```


    @sbt[sbt]@maven[Maven]@gradle[Gradle] runs the gRPC client for Bob

    Note that the difference is the argument `Bob`. The output should include something like:

    ```
    Performing request: Bob
    Performing streaming requests: Bob
    HelloReply(Hello, Bob)
    Bob got streaming reply: Hello, Bob-0
    Bob got streaming reply: Hello, Alice-38
    Bob got streaming reply: Hello, Bob-1
    Bob got streaming reply: Hello, Alice-39
    Bob got streaming reply: Hello, Bob-2
    Bob got streaming reply: Hello, Alice-40
    Bob got streaming reply: Hello, Bob-3
    ```

    Note how the messages from Alice are also received by Bob.


1. Switch back to the console window with the Alice client. The output should include something like:

    ```
    Alice got streaming reply: Hello, Bob-10
    Alice got streaming reply: Hello, Alice-48
    Alice got streaming reply: Hello, Bob-11
    Alice got streaming reply: Hello, Alice-49
    Alice got streaming reply: Hello, Bob-12
    Alice got streaming reply: Hello, Alice-50
    Alice got streaming reply: Hello, Bob-13
    ```

    Note how messages from both Alice and Bob are received in both clients. The streaming request messages are broadcasted
    to all connected clients via the server.


Now take a look at how this is implemented.

You can end the programs with `ctrl-c`.

## What the streaming Hello World does

As you saw in the console output, the example outputs greetings from all clients to all clients. Let’s take at the code and what happens at runtime.

### Server

First, the `GreeterServer` main class is the same as explained in the @ref[first example](index.md#server). It binds the 
`GreeterServiceImpl` to the HTTP server.

We define the interface of the the new call in the protobuf file `src/main/protobuf/helloworld.proto` next to the previous
`SayHello` call:

@@snip [helloworld.proto](/samples/akka-grpc-quickstart-scala/src/main/protobuf/helloworld.proto) { #service-stream }

This method is generated in the `GreeterService` interface and we have to implement it on the server side in `GreeterServiceImpl`:

@@snip [GreeterServiceImpl.scala](/samples/akka-grpc-quickstart-scala/src/main/scala/com/example/helloworld/GreeterServiceImpl.scala) { #import #service-stream }

To connect all input and output streams of all connected clients dynamically we use a [MergeHub](https://doc.akka.io/docs/akka/current/stream/stream-dynamic.html#using-the-mergehub) for the incoming
messages and a [BroadcastHub](https://doc.akka.io/docs/akka/current/stream/stream-dynamic.html#using-the-broadcasthub) for the outgoing messages.

The `MergeHub` and `BroadcastHub` are only needed because we want to connect different clients with each other.
If each client was separate it might look like this to have the stream of incoming messages from one client
transformed and emitted only to that client:

```scala
  override def sayHelloToAll(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    in.map(request => HelloReply(s"Hello, ${request.name}"))
  }
```

### Client

The client is emitting `HelloRequest` once per second and prints the streamed responses:

@@snip [GreeterClient.scala](/samples/akka-grpc-quickstart-scala/src/main/scala/com/example/helloworld/GreeterClient.scala) { #import #client-stream }
