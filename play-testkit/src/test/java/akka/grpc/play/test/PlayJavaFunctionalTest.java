package akka.grpc.play.test;

import example.myapp.helloworld.grpc.helloworld.*;

import org.junit.*;

import play.*;
import play.api.routing.*;
import play.inject.guice.*;
import play.libs.ws.*;
import play.test.*;

import static org.junit.Assert.*;
import static play.inject.Bindings.*;

public final class PlayJavaFunctionalTest extends WithServer {

  @Override
  protected Application provideApplication() {
    return new GuiceApplicationBuilder()
        .overrides(bind(Router.class).to(GreeterServiceImpl.class))
        .build();
  }

  private WSRequest wsUrl(final String path) {
    return WSTestClient.newClient(testServer.port()).url(path);
  }

  @Test public void returns404OnNonGrpcRequest() throws Exception {
    final WSResponse rsp = wsUrl("/").get().toCompletableFuture().get();
    assertEquals(404, rsp.getStatus());
  }

  @Test public void returns200OnNonExistentGrpcMethod() throws Exception {
    final WSResponse rsp = wsUrl("/" + GreeterService$.MODULE$.name() + "/FooBar").get().toCompletableFuture().get();
    assertEquals(200, rsp.getStatus());
  }

}
