# Sample repository for Gradle plugin functional testing

## Run tests against specific Gradle plugin version

Exact version of plugin *must be provided* via java system property.
The version must either be published to Gradle plugin portal or to local maven (`~/.m2`).

```shell script
$ ./gradlew clean test -Dakka.grpc.project.version=1.0.0
```

## Use different versions of Gradle plugin and akka grpc libraries.

Sometimes, it may be required to only tests changes in plugin itself, without publishing a new version of akka grpc libraries to repository.
There's additional parameter for this that may override akka grpc libraries used by plugin.
Libraries must be published to akka grpc release or snapshot repo.

[source,sh]
```shell script
$ ./gradlew clean test -Dakka.grpc.project.version=1.0.0 -Dakka.grpc.baseline.version=0.0.1
```



