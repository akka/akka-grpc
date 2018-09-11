/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.test;

import akka.grpc.play.AkkaGrpcClientHelpers;
import akka.grpc.play.AkkaGrpcClientHelpers$;

import example.myapp.helloworld.grpc.helloworld.*;

import org.junit.*;

import play.api.test.TestServerFactory;
import scala.concurrent.Future;
import scala.reflect.ClassTag$;

import scala.compat.java8.FutureConverters$;

import play.*;
import play.api.routing.*;
import play.api.test.DefaultTestServerFactory$;
import play.api.test.NewTestServer;
import play.inject.guice.*;
import play.libs.ws.*;
import play.test.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static play.inject.Bindings.*;

public final class PlayJavaFunctionalTest implements AkkaGrpcClientHelpers {
  private static final GreeterService$ GreeterService = GreeterService$.MODULE$;
  private static final AkkaGrpcClientHelpers$ AkkaGrpcClientHelpers = AkkaGrpcClientHelpers$.MODULE$;
  private static final ClassTag$ ClassTag = ClassTag$.MODULE$;
  private static final FutureConverters$ FutureConverters = FutureConverters$.MODULE$;

  private final TestServerFactory testServerFactory = DefaultTestServerFactory$.MODULE$;

  private Application app;
  private NewTestServer testServer;
  private GreeterServiceClient greeterServiceClient;

  private Application provideApplication() {
    return new GuiceApplicationBuilder()
        .overrides(bind(Router.class).to(GreeterServiceImpl.class))
        .build();
  }

  @Before
  public void startServer() throws Exception {
    if (testServer != null)
      testServer.stopServer().close();
    app = provideApplication();
    testServer = testServerFactory.start(app.asScala());
    greeterServiceClient = AkkaGrpcClientHelpers.<GreeterServiceClient>factoryForAppEndpoints(
        app.asScala(),
        testServer.endpoints(),
        ClassTag.apply(GreeterServiceClient.class)
    ).create();
  }

  @After
  public void stopServer() throws Exception {
    if (testServer != null) {
      testServer.stopServer().close();
      testServer = null;
      app = null;
    }
    if (greeterServiceClient != null) {
      FutureConverters.toJava(greeterServiceClient.close())
          .toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
  }

  private WSRequest wsUrl(final String path) {
    return WSTestClient.newClient(testServer.endpoints().httpEndpoint().get().port()).url(path);
  }

  @Test public void returns404OnNonGrpcRequest() throws Exception {
    final WSResponse rsp = wsUrl("/").get().toCompletableFuture().get();
    assertEquals(404, rsp.getStatus());
  }

  @Test public void returns200OnNonExistentGrpcMethod() throws Exception {
    final WSResponse rsp = wsUrl("/" + GreeterService.name() + "/FooBar").get().toCompletableFuture().get();
    assertEquals(200, rsp.getStatus());
  }

  @Test public void returns500OnEmptyRequestToAGrpcMethod() throws Exception {
    final WSResponse rsp = wsUrl("/" + GreeterService.name() + "/SayHello").get().toCompletableFuture().get();
    assertEquals(500, rsp.getStatus());
  }

  @Test public void worksWithAGrpcClient() throws Exception {
    final Future<HelloReply> rsp = greeterServiceClient.sayHello(new HelloRequest("Alice"));
    final HelloReply helloReply = FutureConverters.toJava(rsp).toCompletableFuture().get();
    assertEquals("Hello, Alice!", helloReply.message());
  }

}
