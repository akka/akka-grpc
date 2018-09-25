/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

// #service-impl
package controllers; // TODO: Move into 'services' package?

import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** User implementation, with support for dependency injection etc */
@Singleton
public class GreeterServiceImpl implements GreeterService {

  @Inject
  public GreeterServiceImpl() {
  }


  // FIXME: We don't actually need a Materializer here for this example, but should we include it anyway?
  //  @Inject
  //  public GreeterServiceImpl(Materializer mat) {
  //    super(mat);
  //  }

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    String message = String.format("Hello, %s!", in.getName());
    HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    return CompletableFuture.completedFuture(reply);
  }

}
// #service-impl