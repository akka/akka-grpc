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
addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % "$projectversion$")
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

For a complete overview of the configuration options see @ref[configuration](#Configuration) below.

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
:  @@snip [GreeterClient.scala]($root$/../plugin-tester-scala/src/main/scala/io/grpc/examples/helloworld/GreeterClient.scala) { #full-client }

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

## Configuration

You can configure where your .proto files are located like this:

sbt
:   @@snip[build.sbt]($root$/../plugin-tester-java/build.sbt) { #protoSources }

TODO gradle, maven

TODO describe settings for code generation: scala/java, client/server/both

