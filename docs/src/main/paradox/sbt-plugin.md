# sbt plugin details

The sbt plugin is added to a project like so:

@@@vars
```scala
// in project/plugins.sbt:
addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % "$projectversion$")
// in build.sbt:
enablePlugins(AkkaGrpcPlugin)
```
@@@

It will then by default look for `.proto` files under `src/main/protobuf`.

## Configuration

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

TODO more stuff - generating from classpath rather than directory for example