/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
 * Copyright 2015, gRPC Authors All rights reserved.
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

package akka.grpc.benchmarks.qps;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.BenchmarkService;
import akka.grpc.benchmarks.proto.BenchmarkServiceHandlerFactory;
import akka.grpc.benchmarks.proto.Messages;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.Http;
import akka.http.javadsl.UseHttp2;
import akka.stream.Materializer;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.SharedKillSwitch;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * QPS server using the non-blocking API.
 */
public class AsyncServer {
  private static final Logger log = Logger.getLogger(AsyncServer.class.getName());

  private ActorSystem system;
  private BenchmarkServiceImpl benchmarkService;

  public static void main(String... args) throws Exception {
    ServerConfiguration.Builder configBuilder = ServerConfiguration.newBuilder();
    ServerConfiguration config;
    try {
      config = configBuilder.build(args);
    } catch (Exception e) {
      System.out.println(e.getMessage());
      configBuilder.printUsage();
      return;
    }

    new AsyncServer().run((InetSocketAddress) config.address, config.tls);
  }

  /** Equivalent of "main", but non-static. */
  public void run(InetSocketAddress address, boolean useTls) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config

    Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());

    system = ActorSystem.create("AsyncServer", conf);

    Materializer mat = Materializer.matFromSystem(system);

    benchmarkService = new BenchmarkServiceImpl(mat);

    if (useTls) {
      Http.get(system).bindAndHandleAsync(
        BenchmarkServiceHandlerFactory.create(benchmarkService, mat, system),
        ConnectWithHttps.toHostHttps(address.getHostName(), address.getPort()).withCustomHttpsContext(Utils.serverHttpContext()),
        mat)
        .thenAccept(binding -> {
          System.out.println("gRPC server bound to: https://" + address);
        });
    } else {
      Http.get(system).bindAndHandleAsync(
        BenchmarkServiceHandlerFactory.create(benchmarkService, mat, system),
        ConnectHttp.toHost(address.getHostName(), address.getPort(), UseHttp2.always()),
        mat
        ).thenAccept(binding -> {
        System.out.println("gRPC server bound to: http://" + address);
      });
    }
  }

  public void shutdown() {
    if (benchmarkService != null)
      benchmarkService.shutdown();
    benchmarkService = null;

    if (system != null)
      system.terminate();
    system = null;
  }

  public static class BenchmarkServiceImpl implements BenchmarkService {
    // Always use the same canned response for bidi. This is allowed by the spec.
    private static final int BIDI_RESPONSE_BYTES = 100;
    private static final Messages.SimpleResponse BIDI_RESPONSE = Messages.SimpleResponse
        .newBuilder()
        .setPayload(Messages.Payload.newBuilder()
            .setBody(ByteString.copyFrom(new byte[BIDI_RESPONSE_BYTES])).build())
        .build();

    private final SharedKillSwitch shutdown = KillSwitches.shared("shutdown");
    private final Materializer mat;

    public BenchmarkServiceImpl(Materializer mat) {
      this.mat = mat;
    }

    public void shutdown() {
      shutdown.shutdown();
    }

    @Override
    public CompletionStage<Messages.SimpleResponse> unaryCall(Messages.SimpleRequest request) {
      return CompletableFuture.completedFuture(Utils.makeResponse(request));
    }

    @Override
    public Source<Messages.SimpleResponse, NotUsed> streamingCall(Source<Messages.SimpleRequest, NotUsed> request) {
      return request
        .via(shutdown.flow())
        .map(value -> Utils.makeResponse(value));
    }

    @Override
    public CompletionStage<Messages.SimpleResponse> streamingFromClient(Source<Messages.SimpleRequest, NotUsed> request) {
      CompletionStage<Messages.SimpleRequest> lastSeen =
        request
          .via(shutdown.flow())
          .runWith(Sink.last(), mat);

      return lastSeen.thenApply(last -> Utils.makeResponse(last));
    }

    @Override
    public Source<Messages.SimpleResponse, NotUsed> streamingFromServer(Messages.SimpleRequest request) {
      // send forever, until the client cancels or we shut down
      final Messages.SimpleResponse response = Utils.makeResponse(request);
      return Source.repeat(response).via(shutdown.flow());
    }

    @Override
    public Source<Messages.SimpleResponse, NotUsed> streamingBothWays(Source<Messages.SimpleRequest, NotUsed> request) {
      // receive data forever and send data forever until client cancels or we shut down.
      request.via(shutdown.flow()).runWith(Sink.ignore(), mat);
      return Source.repeat(BIDI_RESPONSE).via(shutdown.flow());
    }
  }
}
