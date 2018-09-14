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

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static play.inject.Bindings.*;

public final class PlayJavaFunctionalTest {
  private final TestServerFactory testServerFactory = new DefaultTestServerFactory();

  private Application app;
  private NewTestServer testServer;

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
  }

  @After
  public void stopServer() throws Exception {
    if (testServer != null) {
      testServer.stopServer().close();
      testServer = null;
      app = null;
    }
  }

  private WSResponse wsGet(final String path) throws Exception {
    final WSClient wsClient = app.injector().instanceOf(WSClient.class);
    final String url = testServer.endpoints().httpEndpoint().get().pathUrl(path);
    return wsClient.url(url).get().toCompletableFuture().get();
  }

  @Test public void returns404OnNonGrpcRequest() throws Exception {
    assertEquals(404, wsGet("/").getStatus()); // Maybe should be a 426, see #396
  }

  @Test public void returns200OnNonExistentGrpcMethod() throws Exception {
    final WSResponse rsp = wsGet("/" + GreeterService.name + "/FooBar");
    assertEquals(200, rsp.getStatus()); // Maybe should be a 426, see #396
  }

  @Test public void returns200OnEmptyRequestToAGrpcMethod() throws Exception {
    final WSResponse rsp = wsGet("/" + GreeterService.name + "/SayHello");
    assertEquals(200, rsp.getStatus()); // Maybe should be a 426, see #396
  }

  @Test public void worksWithAGrpcClient() throws Exception {
    final HelloRequest req = HelloRequest.newBuilder().setName("Alice").build();
    final GrpcClientSettings grpcClientSettings =
        JavaAkkaGrpcClientHelpers.grpcClientSettings(testServer);
    final GreeterServiceClient greeterServiceClient = GreeterServiceClient.create(
        grpcClientSettings, app.asScala().materializer(), app.asScala().actorSystem().dispatcher());
    try {
      final HelloReply helloReply = greeterServiceClient.sayHello(req).toCompletableFuture().get();
      assertEquals("Hello, Alice!", helloReply.getMessage());
    } finally {
      greeterServiceClient.close().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
  }

}
