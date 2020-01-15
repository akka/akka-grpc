# Server Reflection

@@@note

This feature is **experimental**.

It implements version v1alpha of the [upstream standard](https://github.com/grpc/grpc/blob/master/src/proto/grpc/reflection/v1alpha/reflection.proto),
so we might expect subsequent versions of the service to emerge. Also,
the Java/Scala API's to enable this feature may still change in further
versions of Akka gRPC, and future versions of this feature may not work with
services generated with older versions of Akka gRPC.

There may be missing features and bugs in the current implementation. If you
encounter any, you are welcome to share a reproducer in our
[issue tracker](https://github.com/akka/akka-grpc/issues).

@@@

Server Reflection is a [gRPC feature](https://github.com/grpc/grpc/blob/master/doc/server-reflection.md)
that allows 'dynamic' clients, such as command-line tools for debugging, to
discover the protocol used by a gRPC server at run time. They can then use
this metadata to implement things like completion and sending arbitrary
commands.

This is achieved by providing a gRPC service that provides endpoints that
can be used to query this information

## Providing

When providing gRPC Server Reflection, you will be serving multiple services
(at least your own service and the reflection service) as described
@ref[here](walkthrough.md) { #Serving_multiple_services }. The reflection service
can be generated via `ServerReflection`:

Scala
:  @@snip [Main.scala](/sbt-plugin/src/sbt-test/gen-scala-server/04-server-reflection/src/main/scala/example/myapp/helloworld/Main.scala) { #server-reflection }

Java
:  @@snip [Main.java](/sbt-plugin/src/sbt-test/gen-java/02-server-reflection/src/main/java/example/myapp/helloworld/Main.java) { #server-reflection }

## Consuming

The Server Reflection endpoint exposed above can be used for example to consume
the service with [grpc_cli](https://github.com/grpc/grpc/blob/master/doc/command_line_tool.md):

```
$ ./bins/opt/grpc_cli call localhost:8080 helloworld.GreeterService.SayHello "name:\"foo\""
connecting to localhost:8080
Received initial metadata from server:
date : Wed, 08 Jan 2020 16:57:56 GMT
server : akka-http/10.1.10
message: "Hello, foo"

Received trailing metadata from server:
date : Wed, 08 Jan 2020 16:57:56 GMT
Rpc succeeded with OK status
```
