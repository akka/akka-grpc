# Binary compatibility

Conflicting versions in transitive dependencies can make upgrading a painful exercise.

To make this easier, starting with version 1.0.0 Akka gRPC provides binary compatibility.
This means if you use a library that in turn uses Akka gRPC, it should be possible to use
that library with any newer version of Akka gRPC as well (with the exceptions listed below).

This is especially relevant if you depend on one library that depends on Akka gRPC
version 'A', and another library that depends on Akka gRPC version 'B': due to
binary compatibility, you can simply choose the lastest version of Akka gRPC and
use both libraries with that.

## Limitations

### New features

Features introduced in later versions of Akka gRPC may not work with code generated
with a previous version of Akka gRPC.

### Deprecations

Binary compatibility can be broken via a deprecation cycle: an API that has been marked deprecated in version `x.y.0`
may disappear in version `x.(y+1).z`.

### Internal and ApiMayChange API's

Internal API's (designated by the `akka.grpc.internal` package or with the `@InternalApi` annotation) and API's that are still marked `@ApiMayChange` are not guaranteed to remain binary compatible.

Libraries that use such methods may not work in applications that depend on a newer version of Akka gRPC.

## Downstream libraries

We depend on a number of downstream libraries that don't formally maintain
binary compatibility, such as [ScalaPB](https://scalapb.github.io/) (when
generating Scala code) and [grpc-java](https://github.com/grpc/grpc-java/).
When updates to those libraries introduce incompatibilities it will be decided
on a case-by-case basis, based on the expected impact of the change,
whether those should impact Akka gRPC's versioning.
