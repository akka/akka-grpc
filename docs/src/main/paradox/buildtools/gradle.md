# Gradle

To get started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring plugin

This plugin is a wrapper for [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin) and uses it for `.proto` files processing and code generation.
Most of the settings could be configured using related setting of `protobuf-gradle-plugin` itself.
Consult [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin#protobuf-plugin-for-gradle-) documentation for details.

The plugin can generate either Java or Scala classes, and then server and or client for the corresponding language.
By default both client and server are generated and Java or Scala is autodetected depending on the presence of source files with language extension in `src/main`.

### Installation

Follow instructions at [Gradle plugin portal](https://plugins.gradle.org/plugin/com.lightbend.akka.grpc.gradle) to apply plugin.

### Available plugin options

Following options are available for configuring the plugin code generation.
Names and default values are provided.

`build.gradle`
:   @@@vars
    ```gradle
    akkaGrpc {
        generateClient = true
        generateServer = true
        generatePlay = false
        usePlayActions = false
        serverPowerApis = false
        extraGenerators = []       
    }
    ```
    @@@

### Generating server "power APIs"

To additionally generate server "power APIs" that have access to request metata, as described
@ref[here](../server/details.md#accessing-request-metadata), set the `serverPowerApis` option:

`build.gradle`
:   @@@vars
    ```gradle
    akkaGrpc {
      ...
      serverPowerApis = true
    }
    ```
    @@@

## Protoc version

Default version of `protoc` compiler is 3.4.0. 
The version and the location of `protoc` can be changed using `protobuf-gradle-plugin` [settings](https://github.com/google/protobuf-gradle-plugin#locate-external-executables).

## Proto source directory

By default the plugin looks for `.proto` files under 

* `src/main/protobuf`
* `src/main/proto`
* `app/protobuf`
* `app/proto`

Loading `.proto` files from other directories could be configured [using settings](https://github.com/google/protobuf-gradle-plugin#customizing-source-directories)
of `protobuf-gradle-plugin`.

## Loading proto files from artifacts

In gRPC it is common to make the version of the protocol you are supporting
explicit by duplicating the proto definitions in your project.

This is supported by `protobuf-gradle-plugin` and explained [here](https://github.com/google/protobuf-gradle-plugin#protos-in-dependencies).

## Starting your Akka gRPC server from gradle

Build script needs a custom task 

`build.gradle`
:   @@@vars
    ```build.gradle
    task runServer(type: JavaExec) {
      classpath = sourceSets.main.runtimeClasspath
      main = 'com.example.helloworld.GreeterServer'
    }
    ```
    @@@

Then, the server can then be started from the command line with:

```
./gradlew runServer
```

## JDK 8 support

If you want to use TLS-based negotiation on JDK 8 versions prior to
[1.8.0_251](https://www.oracle.com/technetwork/java/javase/8u251-relnotes-5972664.html),
the server requires a special Java agent for ALPN.

Doing this from inside of Gradle requires some configuration in the `build.gradle`:

`build.gradle` for JVM 8 prior to update 251
:   @@@vars
    ```gradle
    configurations {
      alpnagent
    }
    dependencies {
      // Configuration for modules that use Jetty ALPN agent
      alpnagent 'org.mortbay.jetty.alpn:jetty-alpn-agent:2.0.10'
    }
    task runServer(type: JavaExec) {
      classpath = sourceSets.main.runtimeClasspath
      main = 'com.example.helloworld.GreeterServer'
      jvmArgs "-javaagent:" + configurations.alpnagent.asPath
    }
    ```
    @@@

## Play Framework support

See the [Play gRPC documentation](https://developer.lightbend.com/docs/play-grpc/current/play/gradle-support.html) for details.
