# Maven

To get started with Akka gRPC read the @ref[client](../client/index.md) or @ref[server](../server/index.md) introductions.

## Configuring what to generate

The plugin can be configured to generate either Java or Scala classes, and then server and or client for the chosen language.
By default both client and server in Java are generated.

: Java
```xml
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

: Scala
```xml
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

## Proto source directory

By default the plugin looks for `.proto`-files under `src/main/proto` (and `src/main/protobuf`). This can be changed with the `protoPaths` setting,
which is a relative path to the project basedir. The below configuration overrides the proto path to be only `src/main/protobuf`:

```xml
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

TODO this is not supported yet See [#152](https://github.com/akka/akka-grpc/issues/152)


## Starting your Akka gRPC server from Maven

As the server requires a special Java agent for ALPN ([see Akka HTTP docs about HTTP/2](https://doc.akka.io/docs/akka-http/current/server-side/http2.html#application-layer-protocol-negotiation-alpn-))
you need to pass this agent with a `-javaagent` flag to the JVM when running the server.

Doing this from inside of Maven requires a little bit of configuration:


```xml
 <dependencies>
   <dependency>
     <groupId>org.mortbay.jetty.alpn</groupId>
     <artifactId>jetty-alpn-agent</artifactId>
     <version>2.0.9</version>
     <scope>runtime</scope>
   </dependency>
  <dependencies>
  ...
  <plugins>
    <plugin>
      <artifactId>maven-dependency-plugin</artifactId>
        <version>2.5.1</version>
        <executions>
          <execution>
            <id>getClasspathFilenames</id>
            <goals>
              <!-- provides the jars of the classpath as properties inside of Maven
                   so that we can refer to one of the jars in the exec plugin config below -->
              <goal>properties</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>1.6.0</version>
        <configuration>
          <executable>java</executable>
          <arguments>
            <argument>-javaagent:${org.mortbay.jetty.alpn:jetty-alpn-agent:jar}</argument>
            <argument>-classpath</argument>
            <classpath />
            <argument>com.example.MainClass</argument>
          </arguments>
        </configuration>
     </plugin>
  </plugins>
```

The server can then be started from the command line with:

```
mvn compile dependency:properties exec:exec
```


