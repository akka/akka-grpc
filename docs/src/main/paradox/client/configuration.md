# Configuration

A gRPC client is configured with a `GrpcClientSettings` instance. There are a number of ways of creating one and the API
docs are the best reference. An `ActorSystem` is always required as it is used for default configuration and service discovery.

## By Code

The simplest way to create a client is to provide a static host and port.

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple }

Further settings can be added via the `with` methods

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #simple-programmatic }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #simple-programmatic }

## By Configuration

Instead a client can be defined in configuration. All client configurations need to be under `akka.grpc.client`

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Java
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #client-config }

Clients defined in configuration pick up defaults from `reference.conf`:

Scala
:  @@snip [reference](/runtime/src/main/resources/reference.conf) { #defaults }

Java
:  @@snip [reference](/runtime/src/main/resources/reference.conf) { #defaults }

## Using Akka Discovery for Endpoint Discovery

The examples above all use a hard coded host and port for the location of the gRPC service which is the default if you do not configure a `service-discovery-mechanism`.
Alternatively [Akka Discovery](https://developer.lightbend.com/docs/akka-management/current/discovery.html) can be used.
This allows a gRPC client to switch between discovering services via DNS, config, Kubernetes and Consul and others by just changing
the configuration.

To see how to config a particular service discovery mechanism see the [Akka Discovery docs](https://developer.lightbend.com/docs/akka-management/current/discovery.html).
Once it is configured a service discovery mechanism name can either be passed into settings or put in the client's configuration.

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

Java
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #config-service-discovery }

The above example configures the client `project.WithConfigServiceDiscovery` to use `config` based service discovery.

Then to create the `GrpcClientSettings`:

Scala
:  @@snip [GrpcClientSettingsSpec](/runtime/src/test/scala/akka/grpc/GrpcClientSettingsSpec.scala) { #sd-settings }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #sd-settings }

Alternatively if a `SimpleServiceDiscovery` is available else where in your system is can be passed in:

Scala
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/scala/docs/akka/grpc/client/GrpcClientSettingsCompileOnly.scala) { #provide-sd }

Java
:  @@snip [GrpcClientSettingsCompileOnly](/runtime/src/test/java/jdocs/akka/grpc/client/GrpcClientSettingsCompileOnly.java) { #provide-sd }

 
Currently service discovery is only queried on creation of the client. A client can be automatically re-created, and go via service discovery again,
 if a connection can't be established, see the lifecycle section.
 
## Debug logging

To enable fine grained debug running the following logging configuration can be used.

Put this in a file `grpc-debug-logging.properties`:

```
handlers=java.util.logging.ConsoleHandler
io.grpc.netty.level=FINE
java.util.logging.ConsoleHandler.level=FINE
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
```

Run with `-Djava.util.logging.config.file=/path/to/grpc-debug-logging.properties`.
