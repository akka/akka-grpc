/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import com.google.inject.Inject;
import example.myapp.helloworld.grpc.AbstractGreeterServiceRouter;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import play.api.inject.Injector;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** User implementation, with support for dependency injection etc */
@Singleton
public class GreeterServiceImpl extends AbstractGreeterServiceRouter {

  @Inject
  public GreeterServiceImpl(Materializer mat) {
    super(mat);
  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    String message = String.format("Hello, %s!", in.getName());
    HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    return CompletableFuture.completedFuture(reply);
  }

}
