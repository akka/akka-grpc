package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AuthenticatedGreeterClient {

  public static void main(String[] args) throws Exception {

    String serverHost = "127.0.0.1";
    int serverPort = 8082;

    ActorSystem system = ActorSystem.create("HelloWorldClient");

    // Configure the client by code:
    GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt(serverHost, serverPort, system).withTls(false);

    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, system);

      try {
        client.sayHello(HelloRequest.newBuilder().setName("Alice").build()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        throw new RuntimeException("Call should have failed");
      } catch (ExecutionException ex) {
        system.log().warning(ex, "Call without authentication fails as expected");
      }

      HelloReply replyWhenAuthenticated = client.sayHello()
        .addHeader("Token", "XYZ")
        .invoke(HelloRequest.newBuilder().setName("Alice").build()).toCompletableFuture().get(5, TimeUnit.SECONDS);
      system.log().info("Call with authentication succeeds: {}", replyWhenAuthenticated);

      GreeterServiceClient clientWithMeta = client.addRequestHeader("Token", "XYZ");

      HelloReply replyWhenInterceptAuthenticated = clientWithMeta.sayHello()
        .addHeader("Token", "XYZ")
        .invoke(HelloRequest.newBuilder().setName("Alice").build()).toCompletableFuture().get(5, TimeUnit.SECONDS);
      system.log().info("Call with authentication succeeds: {}", replyWhenInterceptAuthenticated);

    } catch (StatusRuntimeException e) {
      System.out.println("Status: " + e.getStatus());
    } catch (Exception e)  {
      System.out.println(e);
    } finally {
      if (client != null) client.close();
      system.terminate();
    }
  }
}
