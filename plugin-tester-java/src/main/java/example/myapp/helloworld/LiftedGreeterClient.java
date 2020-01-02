/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.Duration;

import io.grpc.StatusRuntimeException;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.grpc.GrpcClientSettings;

import example.myapp.helloworld.grpc.*;

class LiftedGreeterClient {
  public static void main(String[] args) throws Exception {

    String serverHost = "127.0.0.1";
    int serverPort = 8080;

    ActorSystem system = ActorSystem.create("HelloWorldClient");
    Materializer materializer = ActorMaterializer.create(system);

    GrpcClientSettings settings = GrpcClientSettings.fromConfig(GreeterService.name, system);
    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, materializer, system.dispatcher());

      singleRequestReply(client);
      streamingRequest(client);
      streamingReply(client, materializer);
      streamingRequestReply(client, materializer);


    } catch (StatusRuntimeException e) {
      System.out.println("Status: " + e.getStatus());
    } catch (Exception e)  {
      System.out.println(e);
    } finally {
      if (client != null) client.close();
      system.terminate();
    }

  }

  //#with-metadata
  private static void singleRequestReply(GreeterServiceClient client) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    CompletionStage<HelloReply> reply = client.sayHello()
        .addHeader("key", "value")
        .invoke(request);
    System.out.println("got single reply: " + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }
  //#with-metadata

  private static void streamingRequest(GreeterServiceClient client) throws Exception {
    List<HelloRequest> requests = Arrays.asList("Alice", "Bob", "Peter")
        .stream().map(name -> HelloRequest.newBuilder().setName(name).build())
        .collect(Collectors.toList());
    CompletionStage<HelloReply> reply = client.itKeepsTalking()
        .addHeader("key", "value")
        .invoke(Source.from(requests));
    System.out.println("got single reply for streaming requests: " +
        reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingReply(GreeterServiceClient client, Materializer mat) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    Source<HelloReply, NotUsed> responseStream = client.itKeepsReplying()
        .addHeader("key", "value")
        .invoke(request);
    CompletionStage<Done> done =
        responseStream.runForeach(reply ->
            System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

  private static void streamingRequestReply(GreeterServiceClient client, Materializer mat) throws Exception {
    Duration interval = Duration.ofSeconds(1);
    Source<HelloRequest, NotUsed> requestStream = Source
        .tick(interval, interval, "tick")
        .zipWithIndex()
        .map(pair -> pair.second())
        .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
        .take(10)
        .mapMaterializedValue(m -> NotUsed.getInstance());

    Source<HelloReply, NotUsed> responseStream = client.streamHellos()
        .addHeader("key", "value")
        .invoke(requestStream);
    CompletionStage<Done> done =
        responseStream.runForeach(reply ->
            System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

}
