# Gradle

To get started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring what to generate

The plugin can be configured to generate either Java or Scala classes, and then server and or client for the chosen language.
By default both client and server are generated and Java or Scala is selected depending on if the build
has the `java` or `scala` plugin.

Java
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
    // These are the default options for a Java project (not necessary to define)
    akkaGrpc {
      language = "Java"
      generateClient = true
      generateServer = true
    }
    repositories {
      mavenLocal()
      mavenCentral()
    }
    ```
    @@@

Scala
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
      id 'scala'
      id 'application'
    }
    apply plugin: 'com.lightbend.akka.grpc.gradle'
    // These are the default options for a Scala project (not necessary to define)
    akkaGrpc {
      language = "Scala"
      generateClient = true
      generateServer = true
    }
    repositories {
      mavenLocal()
      mavenCentral()
    }
    ```
    @@@

### Generating server "power APIs"

To additionally generate server "power APIs" that have access to request metata, as described
@ref[here](../server/walkthrough.md#accessing-request-metadata), set the `serverPowerApis` option:

Java
:   @@@vars
    ```gradle
    akkaGrpc {
      ...
      serverPowerApis = true
    }
    ```
    @@@

Scala
:   @@@vars
    ```gradle
    akkaGrpc {
      ...
      serverPowerApis = true
    }
    ```
    @@@

## Proto source directory

The plugin looks for `.proto` files under `src/main/protobuf`.

Loading proto files from other directories is currently not supported.
If you are interested in this feature you can find more background in issue
[#288](https://github.com/akka/akka-grpc/issues/288)

## Loading proto files from artifacts

In gRPC it is common to make the version of the protocol you are supporting
explicit by duplicating the proto definitions in your project.

When using @ref[sbt](sbt.md) as a build system, we also support loading your
proto definitions from a dependency classpath. This is not yet supported
for Maven and @ref[Gradle](gradle.md). If you are interested in this feature
it is tracked in issue [#152](https://github.com/akka/akka-grpc/issues/152)


## Starting your Akka gRPC server from Gradle

As the server requires a special Java agent for ALPN ([see Akka HTTP docs about HTTP/2](https://doc.akka.io/docs/akka-http/current/server-side/http2.html#application-layer-protocol-negotiation-alpn-))
you need to pass this agent with a `-javaagent` flag to the JVM when running the server.

Doing this from inside of Gradle requires a little bit of configuration in the `build.gradle`:


```gradle
// Define a separate configuration for managing the dependency on Jetty ALPN agent.
configurations {
  alpnagent
}

dependencies {
  // Configuration for modules that use Jetty ALPN agent
  alpnagent 'org.mortbay.jetty.alpn:jetty-alpn-agent:2.0.9'
}

task runServer(type: JavaExec) {
  classpath = sourceSets.main.runtimeClasspath
  main = 'com.example.helloworld.GreeterServer'
  jvmArgs "-javaagent:" + configurations.alpnagent.asPath
}

```

The server can then be started from the command line with:

```
./gradlew runServer
```

## Play Framework support

See the [Play gRPC docs](https://github.com/playframework/play-grpc/blob/master/docs/src/main/paradox/play-framework.md) for details.
