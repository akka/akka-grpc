/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.statefulhelloworld;

import example.myapp.statefulhelloworld.grpc.*;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.util.Timeout;
import static akka.pattern.PatternsCS.ask;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// #stateful-service
public final class GreeterServiceImpl implements GreeterService {

  private final ActorSystem system;
  private final ActorRef greeterActor;
  private final Timeout timeout = Timeout.create(Duration.ofSeconds(5));

  public GreeterServiceImpl(ActorSystem system) {
    this.system = system;
    this.greeterActor = system.actorOf(GreeterActor.props("Hello"), "greeter");
  }

  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    return ask(greeterActor, GreeterActor.GET_GREETING, timeout)
        .thenApply(message ->
          HelloReply.newBuilder()
            .setMessage(((GreeterActor.Greeting) message).greeting)
            .build()
        );
  }

  public CompletionStage<ChangeResponse> changeGreeting(ChangeRequest in) {
    greeterActor.tell(new GreeterActor.ChangeGreeting(in.getNewGreeting()), ActorRef.noSender());
    return CompletableFuture.completedFuture(ChangeResponse.newBuilder().build());
  }

}
// #stateful-service