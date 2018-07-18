# Play Framework


## Serving gRPC from a Play Framework app




## Using a gRPC client in a Play Framework app

Akka gRPC has special support to allow for minimal fuzz injection of generated clients in Play. To enable this you
need to first enable the gRPC plugin as described in the @ref[client docs](client.md) and then add an additional
source generator in `build.sbt`:

```
import akka.grpc.gen.scaladsl.play.PlayScalaClientCodeGenerator
akkaGrpcExtraGenerators += PlayScalaClientCodeGenerator
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

