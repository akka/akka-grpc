# Details

## Accessing request metadata

By default the generated service interfaces don't provide access to the request metadata, only to the request
body (via the RPC method input parameter). If your methods require access to the request @apidoc[Metadata], you can configure
Akka gRPC to generate server "power APIs" that extend the base service interfaces to provide an additional
request metadata parameter to each service method. See the detailed chapters on @ref[sbt](../buildtools/sbt.md), @ref[Gradle](../buildtools/gradle.md)
and @ref[Maven](../buildtools/maven.md) for how to set this build option. Note that this option doesn't effect the
generated client stubs.

@java[Notice: you need to change `GreeterServiceHandlerFactory` to `GreeterServiceHandlerFactoryPowerApiHandlerFactory`.]

@scala[Notice: you need to change `GreeterServiceHandler` to `GreeterServicePowerApiHandler`.]

Here's an example implementation of these server power APIs:

Scala
:  @@snip [GreeterServicePowerApiImpl.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/PowerGreeterServiceImpl.scala) { #full-service-impl }

Java
:  @@snip [GreeterServicePowerApiImpl.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServicePowerApiImpl.java) { #full-service-impl }

## Status codes

To signal an error, you can fail the @scala[`Future`]@java[`CompletionStage`] or `Source` you are returning with a @apidoc[GrpcServiceException] containing the status code you want to return.

For an overview of gRPC status codes and their meaning see [statuscodes.md](https://github.com/grpc/grpc/blob/master/doc/statuscodes.md).

For unary responses:

Scala
:    @@snip[GrpcExceptionHandlerSpec](/interop-tests/src/test/scala/akka/grpc/scaladsl/GrpcExceptionHandlerSpec.scala) { #unary }

Java
:   @@snip[ExceptionGreeterServiceImpl](/interop-tests/src/test/java/example/myapp/helloworld/grpc/ExceptionGreeterServiceImpl.java) { #unary }

For streaming responses:

Scala
:    @@snip[GrpcExceptionHandlerSpec](/interop-tests/src/test/scala/akka/grpc/scaladsl/GrpcExceptionHandlerSpec.scala) { #streaming }

Java
:   @@snip[ExceptionGreeterServiceImpl](/interop-tests/src/test/java/example/myapp/helloworld/grpc/ExceptionGreeterServiceImpl.java) { #streaming }

## Rich error model
Beyond status codes you can also use the [Rich error model](https://grpc.io/docs/guides/error/#richer-error-model).  

This example uses an error model taken from [common protobuf](https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto) but every class that is based on @scala[`scalapb.GeneratedMessage`]@java[`com.google.protobuf.Message`] can be used. 
Build and return the error as an `AkkaGrpcException`:

Scala
:    @@snip[RichErrorModelSpec](/interop-tests/src/test/scala/akka/grpc/scaladsl/RichErrorModelNativeSpec.scala) { #rich_error_model_unary }

Java
:    @@snip[RichErrorModelTest](/plugin-tester-java/src/test/scala/example/myapp/helloworld/RichErrorNativeImpl.java) { #rich_error_model_unary }

Please look @ref[here](../client/details.md) how to handle this on the client.
