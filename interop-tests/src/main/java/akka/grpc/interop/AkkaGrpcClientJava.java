/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop;

import akka.actor.ActorSystem;
import io.grpc.internal.testing.TestUtils;
import io.grpc.testing.integration2.ClientTester;
import io.grpc.testing.integration2.TestServiceClient;
import io.grpc.testing.integration2.Settings;
import scala.Function2;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class AkkaGrpcClientJava extends GrpcClient {

  private final Function2<Settings, ActorSystem, ClientTester> clientTesterFactory;

  public AkkaGrpcClientJava(Function2<Settings, ActorSystem, ClientTester> clientTesterFactory) {
    this.clientTesterFactory = clientTesterFactory;
  }

  public void run(String[] args) {
    TestUtils.installConscryptIfAvailable();
    final Settings settings = Settings.parseArgs(args);

    final ActorSystem sys = ActorSystem.create("AkkaGrpcClientJava");

    final TestServiceClient client = new TestServiceClient(clientTesterFactory.apply(settings, sys));
    client.setUp();

    try {
      client.run(settings);
    }
    finally {
      client.tearDown();
      try {
        Await.result(sys.terminate(), Duration.apply(5, TimeUnit.SECONDS));
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }
}
