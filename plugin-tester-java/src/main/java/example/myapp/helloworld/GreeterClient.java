//#full-client
package example.myapp.helloworld;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

import example.myapp.helloworld.grpc.*;

class GreeterClient {
  public static void main(String[] args) throws Exception {

    String serverHost = "127.0.0.1";
    int serverPort = 8080;
    boolean useTls = true;
    boolean useTestCa = true;
    String serverHostOverride = "foo.test.google.fr";

    SslContext sslContext = null;
    if (useTestCa) {
      try {
        // FIXME issue #89
        sslContext = GrpcSslContexts.forClient().trustManager(TestUtils.loadCert("rootCA.crt")).build();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    NettyChannelBuilder channelBuilder =
      NettyChannelBuilder
        .forAddress(serverHost, serverPort)
        .flowControlWindow(65 * 1024)
        .negotiationType(useTls ? NegotiationType.TLS : NegotiationType.PLAINTEXT)
        .sslContext(sslContext);

    if (useTls && serverHostOverride != null)
      channelBuilder.overrideAuthority(serverHostOverride);

    ManagedChannel channel = channelBuilder.build();

    ActorSystem sys = ActorSystem.create();
    Materializer mat = ActorMaterializer.create(sys);

    try {
      CallOptions callOptions = CallOptions.DEFAULT;

      GreeterService client = null; // FIXME not impl yet? new GreeterServiceClient(channel, callOptions);

      singleRequestReply(client);
      streamingRequest(client);
      streamingReply(client, mat);
      streamingRequestReply(client, mat);


    } catch (StatusRuntimeException e) {
      System.out.println("Status: " + e.getStatus());
    } catch (Exception e)  {
      System.out.println(e);
    } finally {
      channel.shutdown().awaitTermination(10, TimeUnit.SECONDS);
      sys.terminate();
    }

  }

  private static void singleRequestReply(GreeterService client) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    CompletionStage<HelloReply> reply = client.sayHello(request);
    System.out.println("got single reply: " + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingRequest(GreeterService client) throws Exception {
    List<HelloRequest> requests = Arrays.asList("Alice", "Bob", "Peter")
        .stream().map(name -> HelloRequest.newBuilder().setName(name).build())
        .collect(Collectors.toList());
    CompletionStage<HelloReply> reply = client.itKeepsTalking(Source.from(requests));
    System.out.println("got single reply for streaming requests: " +
        reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingReply(GreeterService client, Materializer mat) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    Source<HelloReply, NotUsed> responseStream = client.itKeepsReplying(request);
    CompletionStage<Done> done =
      responseStream.runForeach(reply ->
        System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

  private static void streamingRequestReply(GreeterService client, Materializer mat) throws Exception {
    FiniteDuration interval = Duration.create(100, TimeUnit.SECONDS);
    Source<HelloRequest, NotUsed> requestStream = Source
      .tick(interval, interval, "tick")
      .zipWithIndex()
      .map(pair -> pair.second())
      .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
      .take(10)
      .mapMaterializedValue(m -> NotUsed.getInstance());

    Source<HelloReply, NotUsed> responseStream = client.streamHellos(requestStream);
    CompletionStage<Done> done =
      responseStream.runForeach(reply ->
        System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

}
//#full-client
