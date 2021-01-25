# API Design

When designing a gRPC API, you could take into consideration some of the
[Google Cloud API Design Patterns](https://cloud.google.com/apis/design/design_patterns).

## Methods without request or response

If you want to create an endpoint that takes no parameters or produces no
response, it might be tempting to use the `Empty` type as defined by
Google in their [empty.proto](https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf/empty.proto).

It is recommended to introduce your own (empty) message types, however, as
functionality may grow and this prepares you for adding additional (optional) fields
over time.

## Declare and enforce constraints for your request and response payloads with `protoc-gen-validate`

[`protoc-gen-validate` (PGV)](https://github.com/envoyproxy/protoc-gen-validate) defines
a set of Protobuf options allowing to add additional rules on messages and fields in a declarative
fashion. A set of validators for different languages is also provided, to enforce these rules at
runtime.

### Java support

Validators for Java stubs are provided by [the project itself](https://github.com/envoyproxy/protoc-gen-validate#java).

Follow the instructions there for Maven and Gradle. If your are using sbt, you can get
`validate.proto` into the include path and run the protoc plugin generating the validators
through `sbt-protoc` (@ref[used by Akka gRPC](buildtools/sbt.md#sbt-protoc-settings)) by adding
to your `build.sbt`:
```scala
val pgvVersion = "0.4.1" // latest at the time of writing

libraryDependencies ++= Seq(
  "io.envoyproxy.protoc-gen-validate" % "pgv-java-stub" % pgvVersion % "protobuf,compile",
   ("io.envoyproxy.protoc-gen-validate" % "protoc-gen-validate" % pgvVersion).asProtocPlugin,
)

Compile / PB.targets +=
  Target(PB.gens.plugin("validate"), (Compile / akkaGrpcCodeGeneratorSettings / target).value, Seq("lang=java"))

// PGV Java validators use lambda expressions
Compile / compile / javacOptions ++= Seq("-source", "8", "-target", "8"),
```

### Scala support

Validators for ScalaPB stubs are provided by [scalapb-validate](https://github.com/scalapb/scalapb-validate).

Follow the [documentation](https://scalapb.github.io/docs/validation) to update `project/build.sbt`
in order to generate the validators using `sbt-protoc` (@ref[used by Akka gRPC](buildtools/sbt.md#sbt-protoc-settings)).

With the default parameters and target set by Akka gRPC, additions to your `build.sbt` should be:
```scala
import scalapb.GeneratorOption._

libraryDependencies +=
  "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf"

Compile / PB.targets +=
  scalapb.validate.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
```

The `validate_at_construction` option can be particularly interesting in a server-side context
since method implementations will automatically receive pre-validated requests and will not
be able to return invalid responses. [Rule-based type customization](https://scalapb.github.io/docs/validation/#rule-based-type-customization)
goes one step further by encoding the rules as types, when applicable.
