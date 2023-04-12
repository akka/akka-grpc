/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-service-impl
package example.myapp.helloworld;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.api.HttpBody;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import example.myapp.helloworld.grpc.*;

public class GreeterServiceImpl implements GreeterService {
  private final Materializer mat;

  public GreeterServiceImpl(Materializer mat) {
   this.mat = mat;
  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    System.out.println("sayHello to " + in.getName());
    HelloReply reply = HelloReply.newBuilder()
      .setMessage("Hello, " + in.getName())
      .setTimestamp(Timestamp.newBuilder().setSeconds(1234567890).setNanos(12345).build())
      .build();
    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HttpBody> sayHelloHttp(HelloRequest in) {
    System.out.println("sayHelloHttp to " + in.getName());
    HttpBody reply = HttpBody.newBuilder().setData(
      com.google.protobuf.ByteString.copyFrom("test".getBytes())
    ).build();

    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    System.out.println("sayHello to in stream...");
    return in.runWith(Sink.seq(), mat)
      .thenApply(elements -> {
        String elementsStr = elements.stream().map(elem -> elem.getName())
            .collect(Collectors.toList()).toString();
        return HelloReply.newBuilder().setMessage("Hello, " + elementsStr).build();
      });
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    System.out.println("sayHello to " + in.getName() + " with stream of chars");
    List<Character> characters = ("Hello, " + in.getName())
        .chars().mapToObj(c -> (char) c).collect(Collectors.toList());
    return Source.from(characters)
      .map(character -> {
        return HelloReply.newBuilder().setMessage(String.valueOf(character)).build();
      });
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    System.out.println("sayHello to stream...");
    return in.map(request -> HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
  }
}
//#full-service-impl
