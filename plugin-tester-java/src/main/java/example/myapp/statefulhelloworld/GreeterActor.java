/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.statefulhelloworld;

import akka.actor.AbstractActor;
import akka.actor.Props;

// #actor
public class GreeterActor extends AbstractActor {

  public static class ChangeGreeting {
    public final String newGreeting;
    public ChangeGreeting(String newGreeting) {
      this.newGreeting = newGreeting;
    }
  }
  public static class GetGreeting {}
  public static GetGreeting GET_GREETING = new GetGreeting();

  public static class Greeting {
    public final String greeting;
    public Greeting(String greeting) {
      this.greeting = greeting;
    }
  }

  public static Props props(final String initialGreeting) {
    return Props.create(() -> new GreeterActor(initialGreeting));
  }

  private Greeting greeting;

  public GreeterActor(String initialGreeting) {
    greeting = new Greeting(initialGreeting);
  }

  public AbstractActor.Receive createReceive() {
    return receiveBuilder()
        .match(GetGreeting.class, this::onGetGreeting)
        .match(ChangeGreeting.class, this::onChangeGreeting)
        .build();
  }

  private void onGetGreeting(GetGreeting get) {
    getSender().tell(greeting, getSelf());
  }

  private void onChangeGreeting(ChangeGreeting change) {
    greeting = new Greeting(change.newGreeting);
  }
}
// #actor