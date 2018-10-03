# Releasing

## Releasing

1. Update the version number in the `akka-grpc-xx-stable` project name in the [whitesource web UI](https://saas.whitesourcesoftware.com)
    - For example you'd call the project `akka-grpc-0.2-stable`
1. Create a [new release](https://github.com/akka/akka-grpc/releases/new) with the next tag version (e.g. `v0.2`), title and release description including notable changes mentioning external contributors.
1. Travis CI will start a [CI build](https://travis-ci.org/akka/akka-grpc/builds) for the new tag and publish:
  - SBT Plugin is published to [Bintray](https://bintray.com/akka/sbt-plugin-releases) that is linked the SBT plugins repo (no further steps required) 
  - Gradle plugin directly to the Gradle plugin portal (no further steps required)
  - Library jars to [Bintray](https://bintray.com/akka/maven) that needs to be synced with Maven Central
1. Login to [Bintray](https://bintray.com/akka/maven/akka-grpc) and sync to Maven Central.
1. Update quick start guides with the new version:
  - https://github.com/akka/akka-grpc-quickstart-java.g8
  - https://github.com/akka/akka-grpc-quickstart-scala.g8

Due to https://github.com/akka/akka-grpc/issues/365, when the tag is created
before that commit has been been successfully built for the 'master' branch,
the maven tests for the build of the 'master' branch will fail for that
commit. The release build and the next commit on 'master' should be fine.

## Gradle plugin release details

The Gradle plugin goes directly to the Gradle Plugin Portal. An encrypted `gradle.properties` that includes a
publishing key and password is checked in under `gradle.properties.enc` and is decrypted by a private key known
only to [travis](https://docs.travis-ci.com/user/encrypting-files/).
