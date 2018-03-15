# Client

## Setting up

To get started, place the `.proto` file(s) that describe the interface you want to use in your project's
@sbt[src/main/protobuf]@gradle[src/main/proto].

And add it to your build:

sbt
:   @@@vars
```
in project/plugins.sbt:
  addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % "$projectversion$")
```
@@@
```
in build.sbt:
  enablePlugins(AkkaGrpcPlugin)
```

Gradle
:   @@@vars
```
plugins {
  id 'com.google.protobuf' version '0.8.4'
}
protobuf {
  protoc {
    // Get protobuf from maven central instead of
    // using the installed version:
    artifact = 'com.google.protobuf:protoc:3.4.0'
  }
  plugins {
    akkaGrpc {
      artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:$projectversion$:-assembly@jar"
    }
  }
}
```
@@@

TODO settings for code generation: scala/java, client/server/both

## Generate and use

To use a service, such as the Hello World service described in the @ref:[server documentation](server.md),
you only need protobuf definition (`.proto` files) of the service. No additional dependencies to
the server project is needed.

For example, the a Hello World service:

@@snip [helloworld.proto]($root$/../plugin-tester-scala/src/main/protobuf/helloworld.proto)

There are 4 different types of calls:

* **unary call** - single request that returns a @scala[`Future`]@java[`CompletionStage`] with a single response,
  see `sayHello` in above example
* **client streaming call** - `Source` (stream) of requests from the client that returns a
  @scala[`Future`]@java[`CompletionStage`] with a single response,
  see `itKeepsTalking` in above example
* **server streaming call** - single request that returns a `Source` (stream) of responses,
  see `itKeepsReplying` in above example
* client and server streaming call - `Source` (stream) of requests from the client that returns a
  `Source` (stream) of responses,
  see `streamHellos` in above example

Let's use these 4 calls from a client. Start by generating code from the `.proto` definition with:

sbt
:   @@@vars
```
compile
```
@@@

Gradle
:   @@@vars
```
./gradlew build
```
@@@

A main program that calls the server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/helloworld/GreeterClient.scala) { #full-client }

Java
:  @@snip [GreeterClient.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterClient.java) { #full-client }

## Running

sbt
:   @@@vars
```
runMain io.grpc.examples.helloworld.GreeterClient
```
@@@

Gradle
:   @@@vars
```
./gradlew run
```
@@@

TODO describe java-agent

### Debug logging

To enable fine grained debug running the following logging configuration can be used.

Put this in a file `grpc-debug-logging.properties`:

```
handlers=java.util.logging.ConsoleHandler
io.grpc.netty.level=FINE
java.util.logging.ConsoleHandler.level=FINE
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
```

Run with `-Djava.util.logging.config.file=/path/to/grpc-debug-logging.properties`.
