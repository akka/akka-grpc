# sbt

To get started with Akka gRPC read the [client](client.md) or [server](server.md) introductions.

## Configuring what to generate

It can be configured to just generate either server or client like so:

```scala
// This is the default - both client and server
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client)

// only client
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client)

// only server
akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
```

What language to generate stubs for is also configurable:
```
// default is Scala only
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Scala)

// Java only
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Java)

// Both Java and Scala, by default the 'flat_package' option is enabled generated sources look
// consistent between Scala and Java. With both languages enabled you need to disable that option to
// avoid name conflicts
akkaGrpcTargetLanguages := Seq(AkkaGrpc.Java, AkkaGrpc.Scala)
akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
```

Passing generator parameters to the underlying ScalaPB generators can be done through `akkaGrpcCodeGeneratorSettings`
setting, any specified options will be passed to all underlying generators that are enabled. By default this setting
contains the `flat_package` parameter.

```
akkaGrpcCodeGeneratorSettings += "single_line_to_proto_string"
```

Available parameters are listed in the [ScalaPB documentation](https://scalapb.github.io/sbt-settings.html).

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

## Starting your Akka gRPC server from sbt

As the server requires a special Java agent for ALPN ([see Akka HTTP docs about HTTP/2](https://doc.akka.io/docs/akka-http/current/server-side/http2.html#application-layer-protocol-negotiation-alpn-))
you need to pass this agent with a `-javaagent` flag to the JVM when running the server.

This can be done using the `JavaAgent` sbt plugin.

Add the plugin `project/plugin.sbt`

sbt
:   @@snip [plugin.sbt]($root$/../plugin-tester-scala/project/plugins.sbt) { #java-agent-plugin }

and then tell it to use the ALPN agent:

sbt
:   @@snip [build.sbt]($root$/../plugin-tester-scala/build.sbt) { #alpn }

After that you can run it as usual:

sbt
:   ```
runMain io.grpc.examples.helloworld.GreeterServer
```