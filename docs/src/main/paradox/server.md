# Server

## Setting up

To get started, place the `.proto` file(s) that describe the interface you want to implement in your project's
@sbt[src/main/protobuf]@gradle[src/main/proto].

And add it to your build:

sbt
:   @@@vars
```
in plugins.sbt:
  addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % "$projectversion$")
```
@@@
```
in build.sbt:
  enablePlugins(AkkaGrpcPlugin)
```

Gradle
:   @@@vars
```
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

## Implementing

TODO show the interface trait you should implement and the handler generator/factory to turn it into a route

## Running

TODO describe java-agent