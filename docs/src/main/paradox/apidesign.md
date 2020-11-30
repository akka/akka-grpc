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
