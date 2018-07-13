# Server

## Setting up

To get started, you must obtain or write the @ref[`.proto`](proto.md) file(s) that describe the interface you want to implement and add those files
to your project. Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`] directory.
(See the detailed chapters on @ref[sbt](sbt.md), @ref[Gradle](gradle.md) and @ref[Maven](maven.md) for information on taking .proto definitions from dependencies)

Then add the Akka gRPC plugin to your build:

sbt
:   @@@vars
    ```scala
    // in project/plugins.sbt:
    addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "$projectversion$")
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
        classpath 'gradle.plugin.com.lightbend.akka.grpc:akka-grpc-gradle-plugin:$projectversion$'
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
        <akka.grpc.version>$projectversion$</akka.grpc.version>
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

For a complete overview of the configuration options see the chapter for your build tool, @ref[sbt](sbt.md), @ref[Gradle](gradle.md) or @ref[Maven](maven.md).

## Generate and implement

### Proto

Define the interfaces you want to implement in your project's
@sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`]  file(s).

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
* **client and server streaming call** - `Source` (stream) of requests from the client that returns a
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

### Service implementation

Implement the methods of the service interface in a new class:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServiceImpl.java) { #full-service-impl }

### Server

That service can then be handled by an Akka HTTP server via the generated `GreeterServiceHandler`,
which is a function from `HttpRequest` to @scala[`Future[HttpResponse]`]@java[`CompletionStage<HttpResponse>`].
It returns 404 as response for requests that don't match the path of the service.

The server will run one instance of the implementation and that is then shared between requests,
this mean that it must be thread safe. In the sample above there is no mutable state, for more about safely implementing
servers with state see [stateful](#stateful-services)

A main program that starts a Akka HTTP server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterServer.scala) { #full-server }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServer.java) { #full-server }

Note that it's important to enable HTTP/2 in the configuration of the `ActorSystem`.

```
akka.http.server.preview.enable-http2 = on
```

# Multiple services

When a server handles several services the handlers must be combined with
@scala[`akka.grpc.scaladsl.ServiceHandler.concatOrNotFound`]@java[`akka.grpc.javadsl.ServiceHandler.concatOrNotFound`]

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/CombinedServer.scala) { #concatOrNotFound }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/example/myapp/CombinedServer.java) { #import #concatOrNotFound }

@scala[Note that the `GreeterServiceHandler.partial` and `EchoServiceHandler.partial` are used instead of `apply`
methods to create partial functions that are combined by concatOrNotFound.]

## Running

To run the server with HTTP/2 enabled correctly, you will likely have to configure the Jetty ALPN
agent as described @extref[in the Akka HTTP documentation](akka-http:server-side/http2.html#application-layer-protocol-negotiation-alpn-):

See the detailed chapters on @ref[sbt](sbt.md#starting-your-akka-grpc-server-from-sbt), @ref[Gradle](gradle.md#starting-your-akka-grpc-server-from-gradle)
and @ref[Maven](maven.md#starting-your-akka-grpc-server-from-maven) for details on adding the agent.


## Stateful services

More often than not, the whole point of the implementing a service is to keep state. Since the service implementation
is shared between concurrent incoming requests any state must be thread safe.

There are two recommended ways to deal with this:

 * Make the state immutable – immutable state that is created before or when the service is instantiated and then never changes is safe
 * Put the mutable state inside an actor and interact with it through `ask` from unary methods or `Flow.ask` from streams.

This is an example based on the hello world above, but allowing users to change the greeting through a unary call:

Scala
:  @@snip [GreeterServiceImpl.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/statefulhelloworld/GreeterServiceImpl.scala) { #stateful-service }

Java
:  @@snip [GreeterServiceImpl.java]($root$/../plugin-tester-java/src/main/java/example/myapp/statefulhelloworld/GreeterServiceImpl.java) { #stateful-service }

The `GreeterActor` is implemented like this:

Scala
:  @@snip [GreeterActor.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/statefulhelloworld/GreeterActor.scala) { #actor }

Java
:  @@snip [GreeterActor.java]($root$/../plugin-tester-java/src/main/java/example/myapp/statefulhelloworld/GreeterActor.java) { #actor }

