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
Beyond status codes you can also use the [Rich error model](https://www.grpc.io/docs/guides/error/#richer-error-model). Currently there is no native support for this concept in Akka gRPC. However you can use the following manual approach.

Add the following dependency to receive required classes (that are based on the [common protobuf](https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto)):

`sbt`
:   @@@vars
```sbt
libraryDependencies += "io.grpc" % "grpc-protobuf" % "$grpc.version$"
```
@@@

`gradle`
:   @@@vars
```gradle
dependencies {
     implementation 'io.grpc:grpc-protobuf:$grpc.version$'
}
```
@@@

`maven`
:   @@@vars
```maven
<dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-protobuf</artifactId>
      <version>$grpc.version$</version>
</dependency>
```
@@@

Build and return the error as an exception:

Scala
:    @@snip[RichErrorModelSpec](/interop-tests/src/test/scala/akka/grpc/scaladsl/RichErrorModelSpec.scala) { #rich_error_model_unary }

Java
:    @@snip[RichErrorModelTest](/interop-tests/src/test/java/example/myapp/helloworld/grpc/RichErrorImpl.java) { #rich_error_model_unary }

Please look @ref[here](../client/details.md) how to handle this on the client.