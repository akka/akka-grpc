/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.grpc.interop.GrpcClient;
import io.grpc.internal.testing.TestUtils;
import io.grpc.testing.integration2.ClientTester;
import io.grpc.testing.integration2.TestServiceClient;
import io.grpc.testing.integration2.Settings;
import scala.Function3;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class AkkaGrpcClientJava extends GrpcClient {

  private final Function3<Settings, Materializer, ActorSystem, ClientTester> clientTesterFactory;

  public AkkaGrpcClientJava(Function3<Settings, Materializer, ActorSystem, ClientTester> clientTesterFactory) {
    this.clientTesterFactory = clientTesterFactory;
  }

  public void run(String[] args) {
    TestUtils.installConscryptIfAvailable();
    final Settings settings = Settings.parseArgs(args);

    final ActorSystem sys = ActorSystem.create("AkkaGrpcClientJava");
    final Materializer mat = ActorMaterializer.create(sys);

    final TestServiceClient client = new TestServiceClient(clientTesterFactory.apply(settings, mat, sys));
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
