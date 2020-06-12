# sbt

To get started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring what to generate

It can be configured to just generate either server or client like so:

@@snip[build.sbt](/sbt-plugin/src/sbt-test/gen-scala-server/00-interop/build.sbt) { #sources-both #sources-client #sources-server }

What language to generate stubs for is also configurable:

@@snip[build.sbt](/sbt-plugin/src/sbt-test/gen-scala-server/00-interop/build.sbt) { #languages-scala #languages-java #languages-both }

### Configurations

By default, the plugin will run generators against `.proto` sources in the `Compile` directories (`src/main/protobuf`), as well as the `Test` ones (`src/test/protobuf`) if there are any.

The settings documented above can have different values for each configuration, allowing you for example to generate in `Test`
(and in `Test` only) client stubs for a service defined in `Compile`. If you want to do this,
you have to inherit the `.proto` definitions from `Compile` over to `Test`:

@@snip[build.sbt](/sbt-plugin/src/sbt-test/gen-scala-server/03-test-config/build.sbt) { #test }

If you have other configurations with `.proto` sources (for example `IntegrationTest`), you must first declare them and enable the plugin on them:

@@snip[build.sbt](/sbt-plugin/src/sbt-test/gen-scala-server/03-test-config/build.sbt) { #it }

### Generating server "power APIs"

To additionally generate server "power APIs" that have access to request metata, as described
@ref[here](../server/details.md#accessing-request-metadata), set the `server_power_apis` option:

```
akkaGrpcCodeGeneratorSettings += "server_power_apis"
```

## Passing parameters to the generators

The sbt plugin for Akka-gRPC uses [ScalaPB](https://scalapb.github.io) and `sbt-protoc`. It is possible to tune these libraries if the provided defaults
don't suit your needs.

### ScalaPB settings

Passing generator parameters to the underlying ScalaPB generators can be done through `akkaGrpcCodeGeneratorSettings`
setting, any specified options will be passed to all underlying generators that are enabled. By default this setting
contains the `flat_package` parameter.

```
akkaGrpcCodeGeneratorSettings += "single_line_to_proto_string"
```

Available parameters are listed in the [ScalaPB documentation](https://scalapb.github.io/sbt-settings.html).

### `sbt-protoc` settings

To tune the `sbt-protoc` [additional options](https://github.com/thesamet/sbt-protoc#additional-options) such as the proto source directory
you can configure them like this:


```scala
  .settings(
    inConfig(Compile)(Seq(
      excludeFilter in PB.generate := "descriptor.proto"
    ))
  )
```
The above, for example, removes `descriptor.proto` from the list of files to be processed.

By default protobuf files are looked for in `src/main/protobuf` (and `src/main/proto`).
You can configure where your `.proto` files are located like this:

```
// "sourceDirectory in Compile" is "src/main", so this adds "src/main/proto_custom":
inConfig(Compile)(Seq(
  PB.protoSources += sourceDirectory.value / "proto_custom"
))
```

## Loading proto files from artifacts

Instead of duplicating the `.proto` definitions between server and client projects, you can add artifacts
that contain proto definitions to your build:

```scala
libraryDependencies += "com.example" %% "my-grpc-service" % "1.0.0" % "protobuf-src"
```

This adds just the `.proto` resources to the build, so code generation can
happen locally in your project.

It is also possible to add the `.proto` resources as 'external' includes,
assuming that the artifact also contains the correct generated classes for
this API. This is not always possible, since the upstream artifact may not
contain any generated classes or may contain classes that were were generated
in a way that is incompatible with your intended use. To include an artifact
as an external protobuf source, add it like:

```scala
libraryDependencies += "com.example" %% "my-grpc-service" % "1.0.0" % "protobuf"
```

## JDK 8 support

If you want to use TLS-based negotiation on JDK 8 versions prior to
[1.8.0_251](https://www.oracle.com/technetwork/java/javase/8u251-relnotes-5972664.html),
the server requires a special Java agent for ALPN.
 
See the [Akka HTTP docs about HTTP/2](https://doc.akka.io/docs/akka-http/10.1/server-side/http2.html#application-layer-protocol-negotiation-alpn-))
for more information.

## Starting your Akka gRPC server from sbt

You can start your gRPC application as usual with:

```
runMain io.grpc.examples.helloworld.GreeterServer
```