// #full-example
package com.example.helloworld;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorSystem;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class GreeterServiceImplTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  private static ActorSystem<?> system = testKit.system();
  private static GreeterService service;

  @BeforeClass
  public static void setup() {
    service = new GreeterServiceImpl(system);
  }

  @Test
  public void greeterServiceRepliesToSingleRequest() throws Exception {
    HelloReply reply = service.sayHello(HelloRequest.newBuilder().setName("Bob").build())
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
    HelloReply expected = HelloReply.newBuilder().setMessage("Hello, Bob").build();
    assertEquals(expected, reply);
  }

}
// #full-example
