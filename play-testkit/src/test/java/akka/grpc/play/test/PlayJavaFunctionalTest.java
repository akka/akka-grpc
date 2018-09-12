/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play.test;

import akka.grpc.GrpcClientSettings;
import akka.grpc.play.JavaAkkaGrpcClientHelpers;

import example.myapp.helloworld.grpc.*;

import org.junit.*;

import play.*;
import play.api.routing.*;
import play.api.test.*;
import play.inject.guice.*;
import play.libs.ws.*;
import play.test.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static play.inject.Bindings.*;

public final class PlayJavaFunctionalTest {
  private final TestServerFactory testServerFactory = new DefaultTestServerFactory();

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
    final play.api.Application app = this.app.asScala();
    testServer = testServerFactory.start(app);
    final GrpcClientSettings grpcClientSettings =
        JavaAkkaGrpcClientHelpers.grpcClientSettings(testServer);
    greeterServiceClient = GreeterServiceClient.create(
        grpcClientSettings, app.materializer(), app.actorSystem().dispatcher());
  }

  @After
  public void stopServer() throws Exception {
    if (testServer != null) {
      testServer.stopServer().close();
      testServer = null;
      app = null;
    }
    if (greeterServiceClient != null) {
      greeterServiceClient.close().toCompletableFuture().get(30, TimeUnit.SECONDS);
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
    final WSResponse rsp = wsUrl("/" + GreeterService.name + "/FooBar").get().toCompletableFuture().get();
    assertEquals(200, rsp.getStatus());
  }

  @Ignore public void returns500OnEmptyRequestToAGrpcMethod() throws Exception {
    final WSResponse rsp = wsUrl("/" + GreeterService.name + "/SayHello").get().toCompletableFuture().get();
    assertEquals(500, rsp.getStatus());
  }

  @Test public void worksWithAGrpcClient() throws Exception {
    final HelloRequest req = HelloRequest.newBuilder().setName("Alice").build();
    final HelloReply helloReply = greeterServiceClient.sayHello(req).toCompletableFuture().get();
    assertEquals("Hello, Alice!", helloReply.getMessage());
  }

}
