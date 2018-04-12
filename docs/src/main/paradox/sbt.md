# sbt

To get started with Akka gRPC read the [client](client.md) or [server](server.md) introductions.

## Only generating the server or client side

By default the plugin generates both a client and a server stub for Scala. 

It can be configured to just generate either server or client like so:

```scala
// This is the default
import akka.grpc.gen.scaladsl.ScalaBothCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(ScalaBothCodeGenerator, akkaGrpcCodeGeneratorSettings.value))

// only client
import akka.grpc.gen.scaladsl.ScalaClientCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(ScalaClientCodeGenerator, akkaGrpcCodeGeneratorSettings.value))

// only server
import akka.grpc.gen.scaladsl.ScalaServerCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(ScalaServerCodeGenerator, akkaGrpcCodeGeneratorSettings.value))
``` 

It can also ge configured to generate Java classes:

```scala
// Java version of the default - both client and server code generated
import akka.grpc.gen.javadsl.JavaBothCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(JavaBothCodeGenerator, akkaGrpcCodeGeneratorSettings.value))

// only client
import akka.grpc.gen.javadsl.JavaClientCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(JavaClientCodeGenerator, akkaGrpcCodeGeneratorSettings.value))

// only server
import akka.grpc.gen.javadsl.JavaServerCodeGenerator
akkaGrpcCodeGenerators := Seq(GeneratorAndSettings(JavaServerCodeGenerator, akkaGrpcCodeGeneratorSettings.value))

```

## Loading proto files from artifacts

Instead of duplicating the .proto definitions between server and client projects, you can add artifacts
that contain proto definitions to your build:

```scala
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
