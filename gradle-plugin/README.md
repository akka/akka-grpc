# Akka gRPC Gradle Plugin

Notes on how it works:

The plugin uses a gradle protobuf plugin, and then hooks our custom generators in through the Main class in the
akka-grpc-codegen module (and additionally the Main of akka-grpc-scalapb-protoc-plugin scalapb for when building Scala
projects with gradle)