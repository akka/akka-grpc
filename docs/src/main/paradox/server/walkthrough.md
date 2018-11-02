# Walkthrough

## Setting up

To get started, you must obtain or write the @ref[`.proto`](../proto.md) file(s) that describe the interface you want to implement and add those files
to your project. Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`] directory.
(See the detailed chapters on @ref[sbt](../buildtools/sbt.md), @ref[Gradle](../buildtools/gradle.md) and @ref[Maven](../buildtools/maven.md) for information on taking .proto definitions from dependencies)

Then add the Akka gRPC plugin to your build:

sbt
:   @@@vars
    ```scala
    // in project/plugins.sbt:
    addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "$project.version$")
    addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent
    //
    // in build.sbt:
    enablePlugins(AkkaGrpcPlugin)
    // ALPN agent
    enablePlugins(JavaAgent)
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7" % "runtime;test"
    ```
    @@@

Gradle
:   @@@vars
    ```gradle
    buildscript {
      repositories {
        mavenLocal()
        gradlePluginPortal()
      }
      dependencies {
        // see https://plugins.gradle.org/plugin/com.lightbend.akka.grpc.gradle
        // for the currently latest version.
        classpath 'gradle.plugin.com.lightbend.akka.grpc:akka-grpc-gradle-plugin:$project.version$'
      }
    }
    plugins {
      id 'java'
      id 'application'
    }
    apply plugin: 'com.lightbend.akka.grpc.gradle'
    repositories {
      mavenLocal()
      mavenCentral()
    }
    ```
    @@@

Maven
:   @@@vars
    ```xml
    <project>
      <modelVersion>4.0.0</modelVersion>
      <name>Project name</name>
      <groupId>com.example</groupId>
      <artifactId>my-grpc-app</artifactId>
      <version>0.1-SNAPSHOT</version>
      <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <akka.grpc.version>$project.version$</akka.grpc.version>
        <grpc.version>$grpc.version$</grpc.version>
        <project.encoding>UTF-8</project.encoding>
      </properties>
      <dependencies>
        <dependency>
          <groupId>com.lightbend.akka.grpc</groupId>
          <artifactId>akka-grpc-runtime_2.12</artifactId>
          <version>${akka.grpc.version}</version>
        </dependency>
      </dependencies>
      <build>
        <plugins>
          <plugin>
            <groupId>com.lightbend.akka.grpc</groupId>
            <artifactId>akka-grpc-maven-plugin</artifactId>
            <version>${akka.grpc.version}</version>
            <executions>
              <execution>
                <goals>
                  <goal>generate</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </project>
    ```
    @@@

For a complete overview of the configuration options see the chapter for your build tool, @ref[sbt](../buildtools/sbt.md), @ref[Gradle](../buildtools/gradle.md) or @ref[Maven](../buildtools/maven.md).

## Writing a service definition

Define the interfaces you want to implement in your project's
@sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`]  file(s).

For example, this is the definition of a Hello World service:

@@snip [helloworld.proto](/plugin-tester-scala/src/main/protobuf/helloworld.proto)

## Generating interfaces and stubs

Start by generating code from the `.proto` definition with:

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

From the above definition, Akka gRPC generates interfaces that look like this:

Scala
:  @@snip [helloworld.proto](/plugin-tester-scala/target/scala-2.12/src_managed/main/example/myapp/helloworld/grpc/GreeterService.scala)

Java
:  @@snip [helloworld.proto](/plugin-tester-java/target/scala-2.12/src_managed/main/example/myapp/helloworld/grpc/GreeterService.java)

and model @scala[case ]classes for `HelloRequest` and `HelloResponse`.

The service interface is the same for the client and the server side. On the server side, the service implements the interface,
on the client side the Akka gRPC infrastructure implements a stub that will connect to the remote service when called.

There are 4 different types of calls:

* **unary call** - single request that returns a @scala[`Future`]@java[`CompletionStage`] with a single response,
  see `sayHello` in above example
* **client streaming call** - `Source` (stream) of requests from the client that returns a
  @scala[`Future`]@java[`CompletionStage`] with a single response,
  see `itKeepsTalking` in above example
* **server streaming call** - single request that returns a `Source` (stream) of responses,
  see `itKeepsReplying` in above example
* **client and server streaming call** - `Source` (stream) of requests from the client that returns a
  `Source` (stream) of responses,
  see `streamHellos` in above example

## Implementing the service

Let's implement these 4 calls in a new class:

Scala
:  @@snip [GreeterServiceImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [GreeterServiceImpl.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServiceImpl.java) { #full-service-impl }

## Serving the service with Akka HTTP

Note, how the implementation we just wrote is free from any gRPC related boilerplate. It only uses the generated model and interfaces
from your domain and basic Akka streams classes. We now need to connect this implementation class to the web server to
offer it to clients.

Akka gRPC servers are implemented with Akka HTTP. In addition to the above `GreeterService`, a @scala[`GreeterServiceHandler`]@java[`GreeterServiceHandlerFactory`]
was generated that wraps the implementation with the gRPC functionality to be plugged into an existing Akka HTTP server
app.

You create the request handler by calling @scala[`GreeterServiceHandler(yourImpl)`]@java[`GreeterServiceHandlerFactory.create(yourImpl, ...)`].

@@@ note

The server will reuse the given instance of the implementation, which means that it is shared between (potentially concurrent) requests.
Make sure that the implementation is thread-safe. In the sample above there is no mutable state, so it is safe. For more information
about safely implementing servers with state see the advice about [stateful](#stateful-services) below.

@@@

A complete main program that starts an Akka HTTP server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterServiceImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterServer.scala) { #full-server }

Java
:  @@snip [GreeterServiceImpl.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServer.java) { #full-server }

@@@ note

It's important to enable HTTP/2 in Akka HTTP in the configuration of the `ActorSystem` by setting

```
akka.http.server.preview.enable-http2 = on
```

In the example this was done from the `main` method, but you could also do this from within your `application.conf`.

@@@

The above example does not use TLS and is configured to only serve HTTP/2. 
To allow HTTP and HTTP/2 and gRPC on the same port TLS must be used.
That means that you need to configure your server with TLS information to provide certificates.

TODO Document how to configure TLS ([#352](https://github.com/akka/akka-grpc/issues/352))

@@@ note

[Currently](https://github.com/akka/akka-http/issues/2145), Akka HTTP does not allow concurrent requests on a single HTTP/2 connection
if not configured otherwise. Make sure to provide an argument to the `parallelism` parameter to `bindAndHandleAsync` that
is greater than one to allow processing more than one request at a time from a given client as shown in the above snippet.

@@@

## Serving multiple services

When a server handles several services the handlers must be combined with
@scala[`akka.grpc.scaladsl.ServiceHandler.concatOrNotFound`]@java[`akka.grpc.javadsl.ServiceHandler.concatOrNotFound`]:

Scala
:  @@snip [GreeterServiceImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/CombinedServer.scala) { #concatOrNotFound }

Java
:  @@snip [GreeterServiceImpl.java](/plugin-tester-java/src/main/java/example/myapp/CombinedServer.java) { #import #concatOrNotFound }


@scala[Note that `GreeterServiceHandler.partial` and `EchoServiceHandler.partial` are used instead of `apply`
methods to create partial functions that are combined by `concatOrNotFound`.]

## Running the server

To run the server with HTTP/2 enabled correctly, you will likely have to configure the Jetty ALPN
agent as described @extref[in the Akka HTTP documentation](akka-http:server-side/http2.html#application-layer-protocol-negotiation-alpn-):

See the detailed chapters on @ref[sbt](../buildtools/sbt.md#starting-your-akka-grpc-server-from-sbt), @ref[Gradle](../buildtools/gradle.md#starting-your-akka-grpc-server-from-gradle)
and @ref[Maven](../buildtools/maven.md#starting-your-akka-grpc-server-from-maven) for details on adding the agent.

## Stateful services

More often than not, the whole point of the implementing a service is to keep state. Since the service implementation
is shared between concurrent incoming requests any state must be thread safe.

There are two recommended ways to deal with this:

 * Put the mutable state inside an actor and interact with it through `ask` from unary methods or `Flow.ask` from streams.
 * Keep the state in a thread-safe place. For example, a CRUD application that is backed by a database is thread-safe
   when access to the backing database is (which until recently was THE way that applications dealt with request
   concurrency).

This is an example based on the Hello World above, but allowing users to change the greeting through a unary call:

Scala
:  @@snip [GreeterServiceImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/statefulhelloworld/GreeterServiceImpl.scala) { #stateful-service }

Java
:  @@snip [GreeterServiceImpl.java](/plugin-tester-java/src/main/java/example/myapp/statefulhelloworld/GreeterServiceImpl.java) { #stateful-service }

The `GreeterActor` is implemented like this:

Scala
:  @@snip [GreeterActor.scala](/plugin-tester-scala/src/main/scala/example/myapp/statefulhelloworld/GreeterActor.scala) { #actor }

Java
:  @@snip [GreeterActor.java](/plugin-tester-java/src/main/java/example/myapp/statefulhelloworld/GreeterActor.java) { #actor }

Now the actor mailbox is used to synchronize accesses to the mutable state.
