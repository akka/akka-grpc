# Protobuf Service Descriptors

gRPC uses the Protobuf `.proto` file format to define your messages, services
and some aspects of the code generation.

For an in-depth description see the [Protocol buffers documentation](https://developers.google.com/protocol-buffers/docs/proto3),
but here are a few quick examples:

## Messages

Messages are defined in the same way protobuf definitions are used for serialization:

@@snip [helloworld.proto](/plugin-tester-scala/src/main/protobuf/helloworld.proto) { #messages }

## Services

Those messages can be used to define services:

@@snip [helloworld.proto](/plugin-tester-scala/src/main/protobuf/helloworld.proto) { #services }

Both the request and the response consist of either a single message or a stream of messages.

## Code generation options

There are a number of options that can be set in the `.proto` definition that influence how the code is generated:

@@snip [helloworld.proto](/plugin-tester-scala/src/main/protobuf/helloworld.proto) { #options }

The (optional) [`package`](https://developers.google.com/protocol-buffers/docs/proto3#packages)
in the `.proto` is used to resolve references from one `.proto` file to another.
It can also be used for the package name in the generated code, but it is
common to use the separate `java_package` option to override it. In the Akka gRPC
examples the convention is to choose a `java_package` ending in `.grpc` to
easily distinguish between generated and regular code.

The Java code generation places all message classes in a large class
whose name is determined by the `java_outer_classname` setting. By setting the
`java_multiple_files` option, the message classes will be generated in separate files,
but the 'outer' class is still generated with some metadata and utilities.

The Scala code generation runs with the
[`flat_package` generator option](https://scalapb.github.io/docs/sbt-settings/#additional-options-to-the-generator) enabled by default.
Customizations can be added on a
[per-file and/or per-package basis](https://scalapb.github.io/customizations.html).
