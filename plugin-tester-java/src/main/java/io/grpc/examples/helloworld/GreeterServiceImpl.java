//#full-service-impl
package io.grpc.examples.helloworld;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

public class GreeterServiceImpl implements GreeterService {
  private final Materializer mat;

  public GreeterServiceImpl(Materializer mat) {
   this.mat = mat;
  }


  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    System.out.println("sayHello to " + in.getName());
    HelloReply reply = HelloReply.newBuilder().setMessage("Hello, " + in.getName()).build();
    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, Object> in) {
    System.out.println("sayHello to in stream...");
    return in.runWith(Sink.seq(), mat)
      .thenApply(elements -> {
        return HelloReply.newBuilder().setMessage("Hello, " + elements).build();
      });
  }

  @Override
  public Source<HelloReply, Object> itKeepsReplying(HelloRequest in) {
    System.out.println("sayHello to " + in.getName() + " with stream of chars");
    List<Character> characters = ("Hello, " + in.getName())
        .chars().mapToObj(c -> (char) c).collect(Collectors.toList());
    return Source.from(characters)
      .map(character -> {
        return HelloReply.newBuilder().setMessage(String.valueOf(character)).build();
      })
      .mapMaterializedValue(m -> NotUsed.getInstance()); // FIXME would be nice without this
  }

  @Override
  public Source<HelloReply, Object> streamHellos(Source<HelloRequest, Object> in) {
    System.out.println("sayHello to stream...");
    return in.map(request -> HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
  }
}
//#full-service-impl
