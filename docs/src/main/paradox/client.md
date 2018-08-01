# Client

## Setting up

To get started, you must obtain the @ref[`.proto`](proto.md) file(s) that describe the interface you want to use and add those files to your project.

Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`] directory.
(See the detailed chapters on @ref[sbt](sbt.md), @ref[Gradle](gradle.md) and @ref[Maven](maven.md) for information on taking .proto definitions from dependencies)

Then add the following configuration to your build:

sbt
:   @@@vars
```scala
// in project/plugins.sbt:
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "$projectversion$")
// in build.sbt:
enablePlugins(AkkaGrpcPlugin)
```
@@@

Gradle
:  @@@vars
```gradle
buildscript {
  dependencies {
    // version here is a placeholder,
    // it is replaced with a project dependency during integration tests
    // by adding --include-build <path> to gradlew
    classpath 'com.lightbend.akka.grpc:akka-grpc-gradle-plugin:$projectversion$'
  }
}
plugins {
  id 'java'
  id 'application'
}
apply plugin: 'com.lightbend.akka.grpc.gradle'
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
        <version>${akka.grpc.version}</version>
        <!-- Hook the generate goal into the lifecycle,
             automatically tied to generate-sources -->
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
* **client and server streaming call** - `Source` (stream) of requests from the client that returns a
  `Source` (stream) of responses,
  see `streamHellos` in above example

Let's use these 4 calls from a client. Start by generating code from the `.proto` definition with:

sbt
:   ```
compile
```

Gradle
:   ```
./gradlew build
```

Maven
:   ```
mvn akka-grpc:generate
```

A main program that calls the server with the `GreeterService` looks like this:

Scala
:  @@snip [GreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterClient.scala) { #full-client }

Java
:  @@snip [GreeterClient.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterClient.java) { #full-client }

## Running

sbt
:   ```
runMain io.grpc.examples.helloworld.GreeterClient
```

Gradle
:   ```
./gradlew run
```

Maven
:   ```
mvn akka-grpc:generate compile exec:java -Dexec.mainClass=io.grpc.examples.helloworld.GreeterClient
```

### Configuration 

A gRPC client is configured with a `GrpcClientSettings` instance. There are a number of ways of creating one and the API
docs are the best reference. An `ActorSystem` is always required as it is used for default configuration and service discovery.

The simplest way to create a client is to provide a static host and port.

Scala
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple }

Java
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple }

Further settings can be added via the `with` methods

Scala
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple-programmatic }

Java
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple-programmatic }

Instead a client can be defined in configuration. All client configurations need to be under `akka.grpc.client`

Scala
:  @@snip [GrpcClientSettingsSpec]($root$/../runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Java
:  @@snip [GrpcClientSettingsSpec]($root$/../runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Clients defined in configuration pick up defaults from `reference.conf`:

Scala
:  @@snip [reference]($root$/../runtime/src/main/resources/reference.conf) { #defaults }

Java
:  @@snip [reference]($root$/../runtime/src/main/resources/reference.conf) { #defaults }

#### Akka Discovery

The examples above all use a hard coded host and port for the location of the gRPC service which is the default if you do not configure a `service-discovery-mechanism`.
Alternatively [Akka Discovery](https://developer.lightbend.com/docs/akka-management/current/discovery.html) can be used.
This allows a gRPC client to switch between discovering services via DNS, config, Kubernetes and Consul and others by just changing
the configuration.

To see how to config a particular service discovery mechanism see the [Akka Discovery docs](https://developer.lightbend.com/docs/akka-management/current/discovery.html).
Once it is configured a service discovery mechanism name can either be passed into settings or put in the client's configuration.

Scala
:  @@snip [GrpcClientSettingsSpec]($root$/../runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

Java
:  @@snip [GrpcClientSettingsSpec]($root$/../runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

The above example configures the client `project.WithConfigServiceDiscovery` to use `config` based service discovery.

Then to create the `GrpcClientSettings`:

Scala
:  @@snip [GrpcClientSettingsSpec]($root$/../runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #sd-settings }

Java
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #sd-settings }

Alternatively if a `SimpleServiceDiscovery` is available else where in your system is can be passed in:

Scala
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #provide-sd }

Java
:  @@snip [GrpcClientSettingsCompileOnly]($root$/../runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #provide-sd }

 
Currently service discovery is only queried on creation of the client. A client can be automatically re-created, and go via service discovery again,
 if a connection can't be established, see the lifecycle section.
 
### Lifecycle

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
:  @@snip [RestartingGreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/RestartingGreeterClient.scala) { #restarting-client }

Java
:  @@snip [RestartingGreeterClient.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/RestartingGreeterClient.java) { #restarting-client }


To use the client use `withClient`. The actual client isn't exposed as it should not be stored
any where as it can be replaced when a failure happens.

Scala
:  @@snip [RestartingGreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/RestartingGreeterClient.scala) { #usage }

Java
:  @@snip [RestartingGreeterClient.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/RestartingGreeterClient.java) { #usage }


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

### Request metadata

Default request metadata, for example for authentication, can be provided through the
`GrpcClientSettings` passed to the client when it is created, it will be the base metadata used for each request.

In some cases you will want to provide specific metadata to a single request, this can be done through the "lifted"
client API, each method of the service has an empty parameter list version on the client returning a `RequestBuilder`.

After adding the required metadata the request is done by calling `invoke` with the request parameters.

Scala
:  @@snip [GreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/example/myapp/helloworld/LiftedGreeterClient.scala) { #with-metadata }

Java
:  @@snip [GreeterClient.java]($root$/../plugin-tester-java/src/main/java/example/myapp/helloworld/LiftedGreeterClient.java) { #with-metadata }


