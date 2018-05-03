# sbt

To get started with Akka gRPC read the [client](client.md) or [server](server.md) introductions.

## Only generating the server or client side

It can be configured to just generate either server or client like so:

```scala
// This is the default - both client and server
akkaGrpcTargetStubs := Seq(AkkaGrpc.Server, AkkaGrpc.Client)

// only client
akkaGrpcTargetStubs := Seq(AkkaGrpc.Client)

// only server
akkaGrpcTargetStubs := Seq(AkkaGrpc.Server)
```

What language to generate stubs for is also configurable:
```
// default is Scala only
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Scala)

// Java version of the default - both client and server code generated
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Java)

// Both Java and Scala
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Java, AkkaGrpc.Scala)
```

## Proto source directory

You can configure where your .proto files are located like this:

sbt
:   @@snip[build.sbt]($root$/../plugin-tester-java/build.sbt) { #protoSources }

## Loading proto files from artifacts

Instead of duplicating the `.proto` definitions between server and client projects, you can add artifacts
that contain proto definitions to your build:

```scala
libraryDependencies +=
  "com.example" %% "my-grpc-service" % "1.0.0" % "protobuf"
```