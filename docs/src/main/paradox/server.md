# Server

## Setting up

To get started, you must obtain or write the @ref[`.proto`](proto.md) file(s) that describe the interface you want to implement and add those files
to your project. That can be done in two different ways:

1. Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[``] directory.
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

    Maven
    :   ```
    This feature is not yet available for Maven, see https://github.com/akka/akka-grpc/issues/152
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

Maven
:   @@@vars
```
<project>
  <modelVersion>4.0.0</modelVersion>
  <name>Project name</name>
  <groupId>com.example</groupId>
  <artifactId>my-grpc-app</artifactId>
  <version>0.1-SNAPSHOT</version>
  <dependencies>
    <dependency>
      <groupId>com.lightbend.akka.grpc</groupId>
      <artifactId>akka-grpc-runtime_2.12</artifactId>
      <version>${akka.grpc.project.version}</version>
    </dependency>
    <!-- for loading of cert, issue #89 -->
    <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-testing</artifactId>
      <version>${grpc.version}</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>com.lightbend.akka.grpc</groupId>
        <artifactId>akka-grpc-maven-plugin</artifactId>
        <version>${akka.grpc.project.version}</version>
      </plugin>
    </plugins>
  </build>
  <properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <akka.grpc.project.version>$projectversion$</akka.grpc.project.version>
    <grpc.version>$grpc.version$</grpc.version>
    <project.encoding>UTF-8</project.encoding>
  </properties>
</project>
```
@@@


TODO describe settings for code generation: scala/java, client/server/both

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
:   ```
sbt compile
```

Gradle
:   ```
./gradlew build
```

Maven
:   ```
mvn akka-grpc:generate
```

Implement the methods of the service interface in a new class:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/helloworld/GreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterServiceImpl.java) { #full-service-impl }

That service can then be handled by an Akka HTTP server via the generated `GreeterServiceHandler`,
which is a function from `HttpRequest` to @scala[`Future[HttpResponse]`]@java[`CompletionStage<HttpResponse>`].

A main program that starts a Akka HTTP server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/helloworld/GreeterServer.scala) { #full-server }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterServer.java) { #full-server }

Note that it's important to enable HTTP/2 in the configuration of the `ActorSystem`.

```
akka.http.server.preview.enable-http2 = on
```

## Running

To run the server with HTTP/2 enabled correctly, you will likely have to configure the Jetty ALPN
agent as described @extref[in the Akka HTTP documentation](akka-http:server-side/http2.html#application-layer-protocol-negotiation-alpn-):

sbt
:   @@snip [build.sbt]($root$/../plugin-tester-scala/build.sbt) { #alpn }

After that you can run it as usual:

sbt
:   ```
runMain io.grpc.examples.helloworld.GreeterServer
```

Gradle
:   ```
./gradlew run
```

Maven
:   ```
mvn akka-grpc:generate compile exec:java -Dexec.mainClass=io.grpc.examples.helloworld.GreeterClient
```
