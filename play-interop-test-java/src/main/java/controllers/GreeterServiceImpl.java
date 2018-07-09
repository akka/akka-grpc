/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Would be written by the user, with support for dependency injection etc */
@Singleton
public class GreeterServiceImpl implements GreeterService {

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    String message = String.format("Hello, %s!", in.getName());
    HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    return CompletableFuture.completedFuture(reply);
  }

  @Override
  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
    return null;
  }

  @Override
  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    return null;
  }
}
