# Akka gRPC Interop tests

This project contains `grpc-interop-testing` tests and other tests that require code generation.

The project layout is roughly as follows:

* `src/main/*`: code shared between this subproject and the `00-interop-tests` scripted tests
* `src/main/*/io/grpc/testing/integration2`: infrastructure taken from grpc-java, but generalized to also allow invoking the Akka gRPC implementation
* `src/*/*/akka/grpc/interop`: `grpc-interop-testing` tests
* `src/test/scala/akka/grpc/scaladsl` other tests that require code generation

## grpc-interop-testing tests

Test interoperability between the Akka implementation and the implementation from `io.gpc:grpc-interop-testing`, based on [gRPC's original testset](https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md).

There are 3 ways to run these tests:

### Running as unit tests

The GrpcInteropSpec test in this project will run the test suite using various
combinations of Akka gRPC Java, Akka gRPC Scala and gRPC-Java.

### Running as scripted tests

The 'scripted' '00-interop' tests in the sbt-plugin subproject depends on
some of the resources in this subproject to run the tests, this time including
code generation from the actual sbt plugin.

### Running manually

Running the tests manually is a WiP and might need some local tweaks to work.

Run `AkkaHttpServerAppScala` to start a gRPC server with the server-side of the
tests, which can then be tested against using other gRPC implementations
implementing the client side.

Similarly, `GrpcIoClientApp` can be used for the client side
