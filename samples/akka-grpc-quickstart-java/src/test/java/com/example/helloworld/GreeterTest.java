// #full-example
package com.example.helloworld;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.grpc.GrpcClientSettings;
import akka.http.javadsl.ServerBinding;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class GreeterTest {

  // important to enable HTTP/2 in server ActorSystem's config
  private static final Config config = ConfigFactory
          .parseString("akka.http.server.preview.enable-http2 = on")
          .withFallback(ConfigFactory.defaultApplication());

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config);

  private static ActorSystem<?> serverSystem = testKit.system();
  private static ActorSystem<?> clientSystem;
  private static GreeterServiceClient client;
  
  @BeforeClass
  public static void setup() throws Exception {
    CompletionStage<ServerBinding> bound = new GreeterServer(serverSystem).run();
    // make sure server is bound before using client
    bound.toCompletableFuture().get(5, TimeUnit.SECONDS);

    clientSystem = ActorSystem.create(Behaviors.empty(), "GreeterClient");
    // the host and TLS certificate config are picked up from the config file
    client = GreeterServiceClient.create(
        GrpcClientSettings.fromConfig("helloworld.GreeterService", clientSystem),
        clientSystem
      );
  }

  @AfterClass
  public static void teardown() {
    ActorTestKit.shutdown(clientSystem);
    client = null;
  }

  @Test
  public void greeterServiceRepliesToSingleRequest() throws Exception {
    HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("Alice").build())
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
    HelloReply expected = HelloReply.newBuilder().setMessage("Hello, Alice").build();
    assertEquals(expected, reply);
  }

}
// #full-example
