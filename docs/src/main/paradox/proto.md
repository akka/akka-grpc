# Protobuf file format

gRPC uses the Protobuf `.proto` file format to define your messages, services
and some aspects of the code generation.

For an in-depth description see the [official documentation](https://developers.google.com/protocol-buffers/docs/proto),
but here are a few quick examples:

## Messages

Messages are defined in the same way protobuf definitions are used for serialization:

@@snip [helloworld.proto]($root$/../plugin-tester-scala/src/main/protobuf/helloworld.proto) { #messages }

## Services

Those messages can be used to define services:

@@snip [helloworld.proto]($root$/../plugin-tester-scala/src/main/protobuf/helloworld.proto) { #services }

Both the request and the response consists of either a single message or a stream of messages.

If you want to create an endpoint that takes no parameters or produces no
response, a common convention is to use the `Empty` type as defined by
Google in their [empty.proto](https://github.com/google/protobuf/blob/master/src/google/protobuf/empty.proto).
You can either choose to include `empty.proto` in your project or @ref[include
com.google.protobuf:protobuf-java as a dependency](sbt-plugin.md#loading-proto-files-from-the-classpath).

## Code generation options

There are a number of options that can be set in the .proto definition that influence how the code is generated:

@@snip [helloworld.proto]($root$/../plugin-tester-scala/src/main/protobuf/helloworld.proto) { #options }

The (optional) ['package'](https://developers.google.com/protocol-buffers/docs/proto#packages)
in the `.proto` is used to resolve references from one `.proto` file to another.
It can also be used for the package name in the generated code, but it is
common to use the separate `java_package` option to override it.

For the Java code generation, by default all message classes are placed in a large class
whose name is determined by the `java_outer_classname` setting. By setting the
`java_multiple_files` option, the message classes will be moved outside this outer class,
but the 'outer' class is still generated with some metadata and utilities.

For the Scala code generation, by default the mechanism described in the
[ScalaPB documentation](https://scalapb.github.io/customizations.html) is used,
with the `flat_package` option enabled.