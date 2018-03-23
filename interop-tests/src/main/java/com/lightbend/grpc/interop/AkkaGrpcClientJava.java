package com.lightbend.grpc.interop;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.grpc.testing.integration.Util;
import io.grpc.testing.integration2.ClientTester;
import io.grpc.testing.integration2.TestServiceClient;
import io.grpc.testing.integration2.Settings;
import scala.Function3;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class AkkaGrpcClientJava extends GrpcClient {

  private final Function3<Settings, Materializer, ExecutionContext, ClientTester> clientTesterFactory;

  public AkkaGrpcClientJava(Function3<Settings, Materializer, ExecutionContext, ClientTester> clientTesterFactory) {
    this.clientTesterFactory = clientTesterFactory;
  }

  public void run(String[] args) {
    Util.installConscryptIfAvailable();
    final Settings settings = Settings.parseArgs(args);

    final ActorSystem sys = ActorSystem.create();
    final Materializer mat = ActorMaterializer.create(sys);

    final TestServiceClient client = new TestServiceClient(clientTesterFactory.apply(settings, mat, sys.dispatcher()));
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
