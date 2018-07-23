/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers;

import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloRequest;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

// #using-client
@Singleton
public class MyController extends Controller {

  private final GreeterServiceClient greeterServiceClient;

  @Inject
  public MyController(GreeterServiceClient greeterServiceClient) {
    this.greeterServiceClient = greeterServiceClient;
  }

  public CompletionStage<Result> sayHello(String name) {
    return greeterServiceClient.sayHello(
        HelloRequest.newBuilder()
            .setName(name)
            .build()
    ).thenApply(response ->
      ok("response: " + response.getMessage())
    );
  };

}
// #using-client