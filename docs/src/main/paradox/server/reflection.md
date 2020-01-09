# Server Reflection

@@@note

This feature is **experimental**. The API may still change in further versions
of Akka gRPC, and future versions of this feature may not work with services
generated with older versions of Akka gRPC.

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

## Providing gRPC Server Reflection

When providing gRPC Server Reflection, you will be serving multiple services
(at least your own service and the reflection service) as described
@ref[here](walkthrough.md) { #Serving_multiple_services }. The reflection service
can be generated via `ServerReflection`:

Scala
:  @@snip [Main.scala](/sbt-plugin/src/sbt-test/gen-scala-server/04-server-reflection/src/main/scala/example/myapp/helloworld/Main.scala) { #server-reflection }

Java
:  @@snip [Main.java](/sbt-plugin/src/sbt-test/gen-java/02-server-reflection/src/main/java/example/myapp/helloworld/Main.java) { #server-reflection }