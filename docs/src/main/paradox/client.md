# Client

## Setting up

To get started, you must obtain the `.proto` file(s) that describe the interface you want to use and add those files to your project. That can be done in two different ways:

1. Add `.proto` files to your project's @sbt[`src/main/protobuf`]@gradle[`src/main/proto`]@maven[`src/main/proto`] directory.
1. Add a dependency that contains `.proto` files under the `protobuf` configuration:

    sbt
    :   ```scala
    libraryDependencies +=
      "com.example" %% "my-grpc-service" % "1.0.0" % "protobuf"
    ```

    Gradle
    :   ```
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
:  @@snip [GreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/GreeterClient.scala) { #full-client }

Java
:  @@snip [GreeterClient.java]($root$/../plugin-tester-java/src/main/java/io/grpc/examples/helloworld/GreeterClient.java) { #full-client }

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
