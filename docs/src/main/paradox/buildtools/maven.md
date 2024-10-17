# Maven

:   @@@vars
```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <name>Project name</name>
  <groupId>com.example</groupId>
  <artifactId>my-grpc-app</artifactId>
  <version>0.1-SNAPSHOT</version>
  <properties>
    <akka.grpc.version>$project.version$</akka.grpc.version>
    <grpc.version>$grpc.version$</grpc.version>
    <project.encoding>UTF-8</project.encoding>
  </properties>
  <repositories>
    <repository>
      <id>akka-repository</id>
      <name>Akka library repository</name>
      <url>https://repo.akka.io/maven</url>
    </repository>
  </repositories>
  <pluginRepositories>
    <pluginRepository>
      <id>akka-repository</id>
      <name>Akka library repository</name>
      <url>https://repo.akka.io/maven</url>
    </pluginRepository>
  </pluginRepositories>
  <dependencies>
    <dependency>
      <groupId>com.lightbend.akka.grpc</groupId>
      <artifactId>akka-grpc-runtime_2.13</artifactId>
      <version>${akka.grpc.version}</version>
    </dependency>
    <!-- for loading of cert, issue #89 -->
    <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-testing</artifactId>
      <version>${grpc.version}</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>com.lightbend.akka.grpc</groupId>
        <artifactId>akka-grpc-maven-plugin</artifactId>
        <version>${akka.grpc.version}</version>
        <!-- Hook the generate goal into the lifecycle,
             automatically tied to generate-sources -->
        <executions>
          <execution>
            <goals>
              <goal>generate</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```
@@@

For a step by step getting started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring what to generate

The plugin can be configured to generate either Java or Scala classes, and then server and or client for the chosen language.
By default, both client and server in Java are generated.

Java
:   ```xml
    <plugin>
        <groupId>com.lightbend.akka.grpc</groupId>
        <artifactId>akka-grpc-maven-plugin</artifactId>
        <version>${akka.grpc.version}</version>
        <configuration>
          <language>Java</language>
          <generateClient>false</generateClient>
          <generateServer>true</generateServer>
        </configuration>
    </plugin>
    ```

Scala
:   ```xml
    <plugin>
        <groupId>com.lightbend.akka.grpc</groupId>
        <artifactId>akka-grpc-maven-plugin</artifactId>
        <version>${akka.grpc.version}</version>
        <configuration>
          <language>Scala</language>
          <generateClient>false</generateClient>
          <generateServer>true</generateServer>
        </configuration>
    </plugin>
    ```

### Generating server "power APIs"

To additionally generate server "power APIs" that have access to request metadata, as described
@ref[here](../server/details.md#accessing-request-metadata), set the `serverPowerApis` tag as true:

`pom.xml`
:   ```xml
    <plugin>
        ...
        <configuration>
          ...
          <generatorSettings>
            <serverPowerApis>true</serverPowerApis>
          </generatorSettings>
        </configuration>
    </plugin>
    ```

## Proto source directory

By default the plugin looks for `.proto`-files under `src/main/protobuf` (and `src/main/proto`). This can be changed with the `protoPaths` setting,
which is a relative path to the project basedir. The below configuration overrides the proto path to be only `src/main/protobuf`:

`pom.xml`
:   ```xml
    <plugin>
        <groupId>com.lightbend.akka.grpc</groupId>
        <artifactId>akka-grpc-maven-plugin</artifactId>
        <version>${akka.grpc.version}</version>
        <configuration>
          <protoPaths>
            <protoPath>src/main/protobuf</protoPath>
          </protoPaths>
        </configuration>
    </plugin>
    ```

## Loading proto files from artifacts

Instead of duplicating the `.proto` definitions between server and client projects, you can add artifacts that contain proto definitions to your build.

A full example of a maven build definition can be found [here](https://github.com/akka/akka-grpc/blob/main/plugin-tester-java/pom.xml) which allows to import external protos like this:

Java
:  @@snip[proto imports](/plugin-tester-java/src/main/protobuf/helloworld.proto) { #import-external-proto-files }

The `pom.xml` has to be adjusted as follows. As a first step in the `<build>`, the `maven-dependency-plugin` needs to pull in the artifacts containing the protobuf file. The `<outputDirectory>` is the place where the protos from the dependencies are getting placed into (`target`):

Java
:  @@snip[unpack protos](/plugin-tester-java/pom.xml) { #unpack-protos }

Finally, the `target/proto` directory has to be introduced to the `akka-grpc-maven-plugin` to be picket up during `protoc` compilation. Make sure to include all other folders from the project as well, since the definition of `<protoPaths>` overrides the default:

Java
:  @@snip[unpack protos](/plugin-tester-java/pom.xml) { #all-proto-paths }

## Starting your Akka gRPC server from Maven

You can start your gRPC application as usual with:

```bash
mvn compile exec:exec
```
