# sbt

To get started with Akka gRPC read the [client](client.md) or [server](server.md) introductions.

## Configuring what to generate

It can be configured to just generate either server or client like so:

@@snip[x](/sbt-plugin/src/sbt-test/gen-scala-server/00-interop/build.sbt) { #sources-both #sources-client #sources-server }

What language to generate stubs for is also configurable:

@@snip[x](/sbt-plugin/src/sbt-test/gen-scala-server/00-interop/build.sbt) { #languages-scala #languages-java #languages-both }


Passing generator parameters to the underlying ScalaPB generators can be done through `akkaGrpcCodeGeneratorSettings`
setting, any specified options will be passed to all underlying generators that are enabled. By default this setting
contains the `flat_package` parameter.

```
akkaGrpcCodeGeneratorSettings += "single_line_to_proto_string"
```

Available parameters are listed in the [ScalaPB documentation](https://scalapb.github.io/sbt-settings.html).

## Proto source directory

By default protobuf files are looked for in `src/main/protobuf` and `src/main/proto`
You can configure where your .proto files are located like this:

```
// "sourceDirectory in Compile" is "src/main", so this adds "src/main/proto":
inConfig(Compile)(Seq(
  PB.protoSources += sourceDirectory.value / "proto"
))
```

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

@@snip [plugin.sbt]($root$/../plugin-tester-scala/project/plugins.sbt) { #java-agent-plugin }

and then tell it to use the ALPN agent:

@@snip [build.sbt]($root$/../plugin-tester-scala/build.sbt) { #alpn }

After that you can run it as usual:

```
runMain io.grpc.examples.helloworld.GreeterServer
```
