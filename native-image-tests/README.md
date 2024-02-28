# Projects used to verify native-image support works, run by CI or locally.

* `scala-grpc` akka-grpc-quickstart-scala but with some small changes to make it easier to test and native image config added

## Running locally

`sbt publishLocal` akka-grpc itself.

Build test project with `sbt -Dakka.grpc.version=[local-snapshot-version] nativeImage` and then start the generated native
image.

