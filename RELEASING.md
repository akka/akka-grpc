# Releasing

## When to release

Akka gRPC is released when there is a need for it.

If you want to test an improvement that is not yet released, you can use a
snapshot version: we release all commits to master to the snapshot repository
on [Bintray](https://bintray.com/akka/snapshots/akka-grpc).

## How to release

Create a new issue from the [Release Train Issue Template](docs/release-train-issue-template.md):

```
$ sh ./scripts/create-release-issue.sh 0.x.y
```

## Gradle plugin release details

The Gradle plugin goes directly to the Gradle Plugin Portal. An encrypted `gradle.properties` that includes a
publishing key and password is checked in under `gradle.properties.enc` and is decrypted by a private key known
only to [travis](https://docs.travis-ci.com/user/encrypting-files/).

### Releasing only updated docs

It is possible to release a revised documentation to the already existing release.

1. Create a new branch from a release tag. If a revised documentation is for the `v0.3` release, then the name of the new branch should be `docs/v0.3`.
1. Add and commit `version.sbt` file that pins the version to the one, that is being revised. Also set `isSnapshot` to `false` for the stable documentation links. For example:
    ```scala
    ThisBuild / version := "0.6.1"
    ThisBuild / isSnapshot := false
    ```
1. Make all of the required changes to the documentation.
1. Build documentation locally with:
    ```sh
    sbt akka-grpc-docs/previewSite
    ```
1. If the generated documentation looks good, send it to Gustav:
    ```sh
    rm -r docs/target/site
    sbt akka-grpc-docs/publishRsync
    ```
1. Do not forget to push the new branch back to GitHub.
