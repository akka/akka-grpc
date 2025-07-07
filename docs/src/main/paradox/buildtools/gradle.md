# Gradle

To get started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring plugin

This plugin is a wrapper for [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin) and uses it for `.proto` files processing and code generation.
Most of the settings could be configured using related setting of `protobuf-gradle-plugin` itself.
Consult [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin#protobuf-plugin-for-gradle-) documentation for details.

The plugin can generate either Java or Scala classes, and then server and or client for the corresponding language.
By default both client and server are generated and Java or Scala is autodetected depending on the presence of source files with language extension in `src/main`.

### Installation

@@@note
The Akka dependencies are available from Akka’s secure library repository. To access them you need to use a secure, tokenized URL as specified at https://account.akka.io/token.
@@@

To add the Akka gRPC gradle plugin to a project:

`build.gradle`
:   @@@vars
```gradle
buildscript {
  repositories {
    gradlePluginPortal()
    maven {
      url "https://repo.akka.io/<your token>/secure"
    }
  }
}
plugins {
  id 'java'
  id 'application'
  id 'com.lightbend.akka.grpc.gradle' version '$project.version$'
}
repositories {
  mavenCentral()
  maven {
    url "https://repo.akka.io/<your token>/secure"
  }
}
```
@@@

For a step by step getting started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

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

To additionally generate server "power APIs" that have access to request metadata, as described
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
