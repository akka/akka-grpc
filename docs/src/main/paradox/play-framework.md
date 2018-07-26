# Play Framework

Akka gRPC has special support for both consuming gRPC services through an Akka gRPC client and for implementing
your own gRPC service as a part of a Play Framework application.

## Using a gRPC client in a Play Framework app

Akka gRPC has special support to allow for seamless injection of generated clients in Play. To enable this you
need to first enable the gRPC plugin as described in the @ref[client docs](client.md) and then add an additional
source generator in `build.sbt`:

Scala
:  ```
import akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator
akkaGrpcExtraGenerators += PlayScalaClientCodeGenerator
```

Java
:  ```
import akka.grpc.gen.javadsl.play.PlayJavaClientCodeGenerator
akkaGrpcExtraGenerators += PlayJavaClientCodeGenerator
```

This will generate a Play module that provides all generated clients for injection. The module must be enabled
by adding it to the enabled modules in the `application.conf`.

With the following `helloworld.proto` file:

@@snip[helloworld.proto]($root$/../play-interop-test-scala/src/main/protobuf/helloworld.proto) { #protoSources }

The module file is generated in `example.myapp.helloworld.grpc.helloworld.AkkaGrpcClientModule`.

The exact package of the module will be based on the package the proto files are generated in, configured through
the `java_package` option in the proto-file (if there are multiple different gRPC generated clients the module will
be generated in the longest package prefix shared between the clients).

To hook it into Play, in `application.conf`:

@@snip[application.conf]($root$/../play-interop-test-scala/src/main/resources/application.conf) { #client-module }

The clients are configured with entries under `akka.grpc.client` named after the client (`gRPC` package name dot `ServiceName`),
again, in `application.conf`:

@@snip[application.conf]($root$/../play-interop-test-scala/src/main/resources/application.conf) { #service-client-conf }

If a client is generated and use that does not have an entry defined in `application.conf` it will fail with an exception
when the client is injected. // FIXME depends on #271

You can now use the client in a controller by injecting it:

Scala
:   @@snip[MyController.scala]($root$/../play-interop-test-scala/src/main/scala/controllers/MyController.scala) { #using-client }

Java
:   @@snip[MyController.java]($root$/../play-interop-test-java/src/main/java/controllers/MyController.java) { #using-client }

## Serving gRPC from a Play Framework app

To be able to serve gRPC from a Play Framework app you must enable [HTTP/2 Support](https://www.playframework.com/documentation/2.6.x/AkkaHttpServer#HTTP%2F2-support-%28experimental%29)
with HTTPS and the ALPN agent. (This is still somewhat involved and we hope to simplify it)

Generating classes from the gRPC service definition is done buy adding the Akka gRPC plugin to your sbt build:

sbt
:   @@@vars
    ```scala
    // in project/plugins.sbt:
    addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "$projectversion$")
    ```
    @@@


Then you need to enable the Play server side code generator in `build.sbt`:

Scala
:   ```scala
enablePlugins(AkkaGrpcPlugin)
import akka.grpc.gen.scaladsl.play.PlayScalaServerCodeGenerator
akkaGrpcExtraGenerators += PlayScalaServerCodeGenerator
```

Java
:   ```scala
enablePlugins(AkkaGrpcPlugin)
import akka.grpc.gen.javadsl.play.PlayJavaServerCodeGenerator
akkaGrpcExtraGenerators += PlayJavaServerCodeGenerator
```

The plugin will look for `.proto` service descriptors in `app/protobuf` and output an abstract class per service
that you then implement, so for example for the following protobuf descriptor:

@@snip[helloworld.proto]($root$/../play-interop-test-scala/src/main/protobuf/helloworld.proto) { #protoSources }

You will get an abstract class named `example.myapp.helloworld.grpc.helloworld.AbstractGreeterServiceRouter`.
Create a concrete subclass implementing this wherever you see fit in your project, let's say `controller.GreeterServiceImpl`
like so:

Scala
:   @@snip[GreeterServiceImpl.scala]($root$/../play-interop-test-scala/src/main/scala/controllers/GreeterServiceImpl.scala) { #service-impl }

Java
:   @@snip[GreeterServiceImpl.java]($root$/../play-interop-test-java/src/main/java/controllers/GreeterServiceImpl.java) { #service-impl }

And then add the router to your Play `conf/routes` file. Note that the router alerady knows its own path since it is
based on the package name and service name of the service and therefore the path `/` is enough to get it to end up in the right place
(in this example the path will be `/helloworld.GreeterService`).
It cannot be added at an arbitrary path (if you try to do so an exception will be thrown when the router is started).

```
->     /   controllers.GreeterServiceController
```

A gRPC client can now connect to the server and call the provided services.