# Server

## Setting up

To get started, you must obtain the `.proto` file(s) that describe the interface you want to implement and add those files
to your project. That can be done in two different ways:

1. Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`] directory.
1. Add a dependency that contains `.proto` files under the `protobuf` configuration:

    sbt
    : ```scala
    libraryDependencies +=
      "com.example" %% "my-grpc-service" % "1.0.0" % "protobuf"
    ```

    Gradle
    : ```
    TODO: https://github.com/google/protobuf-gradle-plugin#protos-in-dependencies
    ```

Then add the following configuration to your build:

sbt
:   @@@vars
```scala
// in project/plugins.sbt:
addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % "$projectversion$")
// in build.sbt:
enablePlugins(AkkaGrpcPlugin)
```
@@@

Gradle
:   @@@vars
```gradle
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

## Generate and implement

Define the interfaces you want to implement in your project's
@sbt[src/main/protobuf]@gradle[src/main/proto] file(s).

For example, a Hello World service:

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

Let's implement these 4 calls. Start by generating code from the `.proto` definition with:

sbt
:   @@@vars
```
sbt compile
```
@@@

Gradle
:   @@@vars
```
./gradlew build
```
@@@

Implement the methods of the service interface in a new class:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/GreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterServiceImpl.java) { #full-service-impl }

That service can then be handled by an Akka HTTP server via the generated `GreeterServiceHandler`,
which is a function from `HttpRequest` to @scala[`Future[HttpResponse]`]@java[`CompletionStage<HttpResponse>`].

A main program that starts a Akka HTTP server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/GreeterServer.scala) { #full-server }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterServer.java) { #full-server }

Note that it's important to enable HTTP/2 in the configuration of the `ActorSystem`.

```
akka.http.server.preview.enable-http2 = on
```



## Running

sbt
:   @@@vars
```
runMain io.grpc.examples.helloworld.GreeterServer
```
@@@

Gradle
:   @@@vars
```
./gradlew run
```
@@@

TODO describe java-agent
