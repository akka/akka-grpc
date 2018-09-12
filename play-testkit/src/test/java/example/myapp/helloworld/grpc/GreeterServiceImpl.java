/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld.grpc;

import akka.stream.Materializer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** User implementation, with support for dependency injection etc */
@Singleton
public class GreeterServiceImpl extends AbstractGreeterServiceRouter {
  @Inject public GreeterServiceImpl(final Materializer mat) { super(mat); }

  @Override
  public CompletionStage<HelloReply> sayHello(final HelloRequest in) {
    final String message = String.format("Hello, %s!", in.getName());
    final HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    return CompletableFuture.completedFuture(reply);
  }
}
