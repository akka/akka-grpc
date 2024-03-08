/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-client
package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.http.javadsl.common.SSLContextFactory;
import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import javax.net.ssl.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class MtlsGreeterClient {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("MtlsHelloWorldClient");

    GrpcClientSettings clientSettings =
      GrpcClientSettings.connectToServiceAt("localhost", 8443, system)
        .withSslContext(sslContext());

    GreeterServiceClient client = GreeterServiceClient.create(clientSettings, system);

    CompletionStage<HelloReply> reply = client.sayHello(HelloRequest.newBuilder().setName("Jonas").build());

    reply.whenComplete((response, error) -> {
      if (error == null) {
        System.out.println("Successful reply: " + reply);
      } else {
        System.out.println("Request failed");
        error.printStackTrace();
      }
      system.terminate();
    });
  }

  private static SSLContext sslContext() {
    try {
      return SSLContextFactory.createSSLContextFromPem(
        // Note: these are filesystem paths, not classpath
        Paths.get("plugin-tester-java/src/main/resources/certs/client1.crt"),
        Paths.get("plugin-tester-java/src/main/resources/certs/client1.key"),
        // server cert is issued by this CA
        List.of(Paths.get("plugin-tester-scala/src/main/resources/certs/rootCA.crt"))
      );
    } catch (Exception ex) {
      throw new RuntimeException("Failed to set up SSL context for the client", ex);
    }
  }
}
//#full-client
