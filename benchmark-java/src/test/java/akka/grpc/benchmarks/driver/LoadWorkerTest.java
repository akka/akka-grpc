/*
 * Copyright (C) 2018 Lightbend Inc. <http://www.lightbend.com>
 * Copyright 2016, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.grpc.benchmarks.driver;

import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.grpc.GrpcClientSettings;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.Control;
import akka.grpc.benchmarks.proto.Stats;
import akka.grpc.benchmarks.proto.WorkerServiceClient;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.SharedKillSwitch;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.scalatest.junit.JUnitSuite;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Basic tests for {@link akka.grpc.benchmarks.driver.LoadWorker}
 */
@RunWith(JUnit4.class)
public class LoadWorkerTest extends JUnitSuite {


  private static final int TIMEOUT = 5;
  private static final Control.ClientArgs MARK = Control.ClientArgs.newBuilder()
      .setMark(Control.Mark.newBuilder().setReset(true).build())
      .build();

  private LoadWorker worker;
  private WorkerServiceClient workerServiceStub;

  private ActorSystem system;
  private Materializer mat;

  @Before
  public void setup() throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());
    system = ActorSystem.create("LoadWorkerTest", conf);
    mat = ActorMaterializer.create(system);

    int port = Utils.pickUnusedPort();
    worker = new LoadWorker(system, port, 0);
    worker.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
    GrpcClientSettings settings = Utils.createGrpcClientSettings(new InetSocketAddress("127.0.0.1", port), true);
    workerServiceStub = WorkerServiceClient.create(settings, mat, system.dispatcher());
  }

  @After
  public void tearDown() throws Exception {
    if (system != null) {
      workerServiceStub.close();
      system.terminate();
      system.getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
  }

  @Ignore // SYNC_CLIENT not implemented
  public void runUnaryBlockingClosedLoop() throws Exception {
    Control.ServerArgs.Builder serverArgsBuilder = Control.ServerArgs.newBuilder();
    serverArgsBuilder.getSetupBuilder()
        .setServerType(Control.ServerType.ASYNC_SERVER)
        .setAsyncServerThreads(4)
        .setPort(0)
        .getPayloadConfigBuilder().getSimpleParamsBuilder().setRespSize(1000);
    int serverPort = startServer(serverArgsBuilder.build());

    Control.ClientArgs.Builder clientArgsBuilder = Control.ClientArgs.newBuilder();
    String serverAddress = "localhost:" + serverPort;
    clientArgsBuilder.getSetupBuilder()
        .setClientType(Control.ClientType.SYNC_CLIENT)
        .setRpcType(Control.RpcType.UNARY)
        .setClientChannels(2)
        .setOutstandingRpcsPerChannel(2)
        .addServerTargets(serverAddress);
    clientArgsBuilder.getSetupBuilder().getPayloadConfigBuilder().getSimpleParamsBuilder()
        .setReqSize(1000)
        .setRespSize(1000);
    clientArgsBuilder.getSetupBuilder().getHistogramParamsBuilder()
        .setResolution(0.01)
        .setMaxPossible(60000000000.0);
    assertWorkOccurred(clientArgsBuilder.build());
  }

  @Test
  public void runUnaryAsyncClosedLoop() throws Exception {
    Control.ServerArgs.Builder serverArgsBuilder = Control.ServerArgs.newBuilder();
    serverArgsBuilder.getSetupBuilder()
        .setServerType(Control.ServerType.ASYNC_SERVER)
        .setAsyncServerThreads(4)
        .setPort(0)
        .getPayloadConfigBuilder().getSimpleParamsBuilder().setRespSize(1000);
    int serverPort = startServer(serverArgsBuilder.build());

    Control.ClientArgs.Builder clientArgsBuilder = Control.ClientArgs.newBuilder();
    String serverAddress = "localhost:" + serverPort;
    clientArgsBuilder.getSetupBuilder()
        .setClientType(Control.ClientType.ASYNC_CLIENT)
        .setClientChannels(2)
        .setRpcType(Control.RpcType.UNARY)
        .setOutstandingRpcsPerChannel(1)
        .setAsyncClientThreads(4)
        .addServerTargets(serverAddress);
    clientArgsBuilder.getSetupBuilder().getPayloadConfigBuilder().getSimpleParamsBuilder()
        .setReqSize(1000)
        .setRespSize(1000);
    clientArgsBuilder.getSetupBuilder().getHistogramParamsBuilder()
        .setResolution(0.01)
        .setMaxPossible(60000000000.0);
    assertWorkOccurred(clientArgsBuilder.build());
  }

  @Test
  public void runPingPongAsyncClosedLoop() throws Exception {
    Control.ServerArgs.Builder serverArgsBuilder = Control.ServerArgs.newBuilder();
    serverArgsBuilder.getSetupBuilder()
        .setServerType(Control.ServerType.ASYNC_SERVER)
        .setAsyncServerThreads(4)
        .setPort(0)
        .getPayloadConfigBuilder().getSimpleParamsBuilder().setRespSize(1000);
    int serverPort = startServer(serverArgsBuilder.build());

    Control.ClientArgs.Builder clientArgsBuilder = Control.ClientArgs.newBuilder();
    String serverAddress = "localhost:" + serverPort;
    clientArgsBuilder.getSetupBuilder()
        .setClientType(Control.ClientType.ASYNC_CLIENT)
        .setClientChannels(2)
        .setRpcType(Control.RpcType.STREAMING)
        .setOutstandingRpcsPerChannel(1)
        .setAsyncClientThreads(4)
        .addServerTargets(serverAddress);
    clientArgsBuilder.getSetupBuilder().getPayloadConfigBuilder().getSimpleParamsBuilder()
        .setReqSize(1000)
        .setRespSize(1000);
    clientArgsBuilder.getSetupBuilder().getHistogramParamsBuilder()
        .setResolution(0.01)
        .setMaxPossible(60000000000.0);
    assertWorkOccurred(clientArgsBuilder.build());
  }

  @Ignore // ASYNC_GENERIC_SERVER not implemented
  public void runGenericPingPongAsyncClosedLoop() throws Exception {
    Control.ServerArgs.Builder serverArgsBuilder = Control.ServerArgs.newBuilder();
    serverArgsBuilder.getSetupBuilder()
        .setServerType(Control.ServerType.ASYNC_GENERIC_SERVER)
        .setAsyncServerThreads(4)
        .setPort(0)
        .getPayloadConfigBuilder().getBytebufParamsBuilder().setReqSize(1000).setRespSize(1000);
    int serverPort = startServer(serverArgsBuilder.build());

    Control.ClientArgs.Builder clientArgsBuilder = Control.ClientArgs.newBuilder();
    String serverAddress = "localhost:" + serverPort;
    clientArgsBuilder.getSetupBuilder()
        .setClientType(Control.ClientType.ASYNC_CLIENT)
        .setClientChannels(2)
        .setRpcType(Control.RpcType.STREAMING)
        .setOutstandingRpcsPerChannel(1)
        .setAsyncClientThreads(4)
        .addServerTargets(serverAddress);
    clientArgsBuilder.getSetupBuilder().getPayloadConfigBuilder().getBytebufParamsBuilder()
        .setReqSize(1000)
        .setRespSize(1000);
    clientArgsBuilder.getSetupBuilder().getHistogramParamsBuilder()
        .setResolution(0.01)
        .setMaxPossible(60000000000.0);
    assertWorkOccurred(clientArgsBuilder.build());
  }

  private void assertWorkOccurred(Control.ClientArgs clientArgs) throws Exception {

    LinkedBlockingQueue<Stats.ClientStats> marksQueue = new LinkedBlockingQueue<Stats.ClientStats>();

    SharedKillSwitch killSwitch = KillSwitches.shared("quit");

    Source<Control.ClientArgs, Cancellable> marksPolling =
        Source.tick(Duration.ofMillis(300), Duration.ofMillis(300), MARK)
          .via(killSwitch.flow());

    Source.single(clientArgs);

    CompletionStage<Stats.ClientStats> clientStats = workerServiceStub.runClient(Source.single(clientArgs).concat(marksPolling))
        .map(clientStatus -> clientStatus.getStats())
        .filter(stats -> stats.hasLatencies() && stats.getLatencies().getCount() > 10)
        .runWith(Sink.head(), mat);

    Stats.ClientStats stat = clientStats.toCompletableFuture().get(TIMEOUT, TimeUnit.SECONDS);

    assertTrue(stat.hasLatencies());
    assertTrue(stat.getLatencies().getCount() < stat.getLatencies().getSum());
    double mean = stat.getLatencies().getSum() / stat.getLatencies().getCount();
    System.out.println("Mean " + mean + " us");
    assertTrue(mean > stat.getLatencies().getMinSeen());
    assertTrue(mean < stat.getLatencies().getMaxSeen());
  }

  private int startServer(Control.ServerArgs serverArgs) throws Exception {
    CompletionStage<Control.ServerStatus> response = workerServiceStub.runServer(Source.single(serverArgs))
        .runWith(Sink.head(), mat);
    Control.ServerStatus serverStatus = response.toCompletableFuture().get(TIMEOUT, TimeUnit.SECONDS);
    return serverStatus.getPort();
  }
}
