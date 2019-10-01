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

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.BenchmarkServiceClient;
import akka.grpc.benchmarks.proto.Messages.Payload;
import akka.grpc.benchmarks.proto.Messages.SimpleRequest;
import akka.grpc.benchmarks.proto.Messages.SimpleResponse;
import akka.stream.Materializer;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static akka.grpc.benchmarks.Utils.*;
import static akka.grpc.benchmarks.qps.ClientConfiguration.ClientParam.*;

/**
 * QPS Client using the non-blocking API.
 */
public class AsyncClient {

  private final ClientConfiguration config;
  private ActorSystem system;
  private Materializer mat;

  public AsyncClient(ClientConfiguration config) {
    this.config = config;

    this.system = ActorSystem.create("AsyncClient");
    this.mat = Materializer.matFromSystem(system);
  }

  /**
   * Start the QPS Client.
   */
  public void run() throws Exception {
    if (config == null) {
      return;
    }

    SimpleRequest req = newRequest();

    InetSocketAddress socketAddress = (InetSocketAddress) config.address;

    if (!config.tls)
      system.log().info("Using plaintext gRPC HTTP/2 connections");

    GrpcClientSettings settings = Utils.createGrpcClientSettings(socketAddress, config.tls, system);

    List<BenchmarkServiceClient> clients = new ArrayList<BenchmarkServiceClient>(config.channels);
    for (int i = 0; i < config.channels; i++) {
      BenchmarkServiceClient client = BenchmarkServiceClient.create(settings, mat, system.dispatcher());
      clients.add(client);
    }

    // Do a warmup first. It's the same as the actual benchmark, except that
    // we ignore the statistics.
    warmup(req, clients);

    long startTime = System.nanoTime();
    long endTime = startTime + TimeUnit.SECONDS.toNanos(config.duration);
    List<Histogram> histograms = doBenchmark(req, clients, endTime);
    long elapsedTime = System.nanoTime() - startTime;

    Histogram merged = merge(histograms);

    printStats(merged, elapsedTime);
    if (config.histogramFile != null) {
      saveHistogram(merged, config.histogramFile);
    }
    shutdown(clients);
  }

  private SimpleRequest newRequest() {
    ByteString body = ByteString.copyFrom(new byte[config.clientPayload]);
    Payload payload = Payload.newBuilder().setType(config.payloadType).setBody(body).build();

    return SimpleRequest.newBuilder()
            .setResponseType(config.payloadType)
            .setResponseSize(config.serverPayload)
            .setPayload(payload)
            .build();
  }

  private void warmup(SimpleRequest req, List<BenchmarkServiceClient> clients) throws Exception {
    long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.warmupDuration);
    doBenchmark(req, clients, endTime);
    // I don't know if this helps, but it doesn't hurt trying. We sometimes run warmups
    // of several minutes at full load and it would be nice to start the actual benchmark
    // with a clean heap.
    System.gc();
  }

  private List<Histogram> doBenchmark(SimpleRequest req,
                                      List<BenchmarkServiceClient> clients,
                                      long endTime) throws Exception {
    // Initiate the concurrent calls
    List<Future<Histogram>> futures = new ArrayList<Future<Histogram>>(config.outstandingRpcsPerChannel);
    for (int i = 0; i < config.channels; i++) {
      for (int j = 0; j < config.outstandingRpcsPerChannel; j++) {
        BenchmarkServiceClient client = clients.get(i);
        futures.add(doRpcs(client, req, endTime));
      }
    }
    // Wait for completion
    List<Histogram> histograms = new ArrayList<Histogram>(futures.size());
    for (Future<Histogram> future : futures) {
      histograms.add(future.get());
    }
    return histograms;
  }

  private Future<Histogram> doRpcs(BenchmarkServiceClient client, SimpleRequest request, long endTime) {
    switch (config.rpcType) {
      case UNARY:
        return doUnaryCalls(client, request, endTime);
      case STREAMING:
        return doStreamingCalls(client, request, endTime);
      default:
        throw new IllegalStateException("unsupported rpc type");
    }
  }

  private Future<Histogram> doUnaryCalls(BenchmarkServiceClient client, final SimpleRequest request,
                                         final long endTime) {
    final Histogram histogram = new Histogram(HISTOGRAM_MAX_VALUE, HISTOGRAM_PRECISION);
    final HistogramFuture future = new HistogramFuture(histogram);

    final AtomicLong lastCall = new AtomicLong();
    lastCall.set(System.nanoTime());
    CompletionStage<SimpleResponse> response = client.unaryCall(request);

    final BiConsumer<SimpleResponse, ? super Throwable> responseCallback = new BiConsumer<SimpleResponse, Throwable>() {
      long lastCall = System.nanoTime();

      @Override
      public void accept(SimpleResponse rsp, Throwable t) {
        if (t == null) {
          long now = System.nanoTime();
          // Record the latencies in microseconds
          histogram.recordValue((now - lastCall) / 1000);
          lastCall = now;

          if (endTime > now) {
            CompletionStage<SimpleResponse> response = client.unaryCall(request);
            response.whenComplete(this); // use same BiConsumer instance
          } else {
            future.done();
          }
        } else {
          Status status = Status.fromThrowable(t);
          System.err.println("Encountered an error in unaryCall. Status is " + status);
          t.printStackTrace();
          future.cancel(true);
        }

      }
    };

    // FIXME whenCompleteAsync?
    response.whenComplete(responseCallback);

    return future;
  }

  private Future<Histogram> doStreamingCalls(BenchmarkServiceClient client, final SimpleRequest request,
                                                    final long endTime) {
    final Histogram histogram = new Histogram(HISTOGRAM_MAX_VALUE, HISTOGRAM_PRECISION);
    final HistogramFuture future = new HistogramFuture(histogram);

    final AtomicLong lastCall = new AtomicLong();
    lastCall.set(System.nanoTime());
    final AtomicReference<ActorRef> requestIngress = new AtomicReference<>();
    Source<SimpleRequest, NotUsed> requestSource =
        Source.<SimpleRequest> actorRef(2, OverflowStrategy.fail())
        .mapMaterializedValue(ref -> {
          requestIngress.set(ref);
          requestIngress.get().tell(request, ActorRef.noSender());
          return NotUsed.getInstance();
        });
    CompletionStage<Done> done = client.streamingCall(requestSource)
        .runWith(Sink.foreach(rsp -> {

          long now = System.nanoTime();
          // Record the latencies in microseconds
          histogram.recordValue((now - lastCall.get()) / 1000);
          lastCall.set(now);

          if (endTime > now) {
            requestIngress.get().tell(request, ActorRef.noSender());
          } else {
            requestIngress.get().tell(new akka.actor.Status.Success("done"), ActorRef.noSender());
          }
        }), mat);

    done.whenComplete((d, t) -> {
      if (t == null) {
        future.done();
      } else {
        Status status = Status.fromThrowable(t);
        System.err.println("Encountered an error in streamingCall. Status is " + status);
        t.printStackTrace();

        future.cancel(true);
      }
    });

    return future;
  }


  private static Histogram merge(List<Histogram> histograms) {
    Histogram merged = new Histogram(HISTOGRAM_MAX_VALUE, HISTOGRAM_PRECISION);
    for (Histogram histogram : histograms) {
      for (HistogramIterationValue value : histogram.allValues()) {
        long latency = value.getValueIteratedTo();
        long count = value.getCountAtValueIteratedTo();
        merged.recordValueWithCount(latency, count);
      }
    }
    return merged;
  }

  private void printStats(Histogram histogram, long elapsedTime) {
    long latency50 = histogram.getValueAtPercentile(50);
    long latency90 = histogram.getValueAtPercentile(90);
    long latency95 = histogram.getValueAtPercentile(95);
    long latency99 = histogram.getValueAtPercentile(99);
    long latency999 = histogram.getValueAtPercentile(99.9);
    long latencyMax = histogram.getValueAtPercentile(100);
    long queriesPerSecond = histogram.getTotalCount() * 1000000000L / elapsedTime;

    StringBuilder values = new StringBuilder();
    values.append("Channels:                       ").append(config.channels).append('\n')
          .append("Outstanding RPCs per Channel:   ")
          .append(config.outstandingRpcsPerChannel).append('\n')
          .append("Server Payload Size:            ").append(config.serverPayload).append('\n')
          .append("Client Payload Size:            ").append(config.clientPayload).append('\n')
          .append("50%ile Latency (in micros):     ").append(latency50).append('\n')
          .append("90%ile Latency (in micros):     ").append(latency90).append('\n')
          .append("95%ile Latency (in micros):     ").append(latency95).append('\n')
          .append("99%ile Latency (in micros):     ").append(latency99).append('\n')
          .append("99.9%ile Latency (in micros):   ").append(latency999).append('\n')
          .append("Maximum Latency (in micros):    ").append(latencyMax).append('\n')
          .append("QPS:                            ").append(queriesPerSecond).append('\n');
    System.out.println(values);
  }

  private void shutdown(List<BenchmarkServiceClient> clients) {
    for (BenchmarkServiceClient channel : clients) {
      channel.close();
    }
    system.terminate();
  }

  public static void main(String... args) throws Exception {
    ClientConfiguration.Builder configBuilder = ClientConfiguration.newBuilder(
        ADDRESS, CHANNELS, OUTSTANDING_RPCS, CLIENT_PAYLOAD, SERVER_PAYLOAD,
        TLS, TESTCA, TRANSPORT, DURATION, WARMUP_DURATION,
        SAVE_HISTOGRAM, STREAMING_RPCS);
    ClientConfiguration config;
    try {
      config = configBuilder.build(args);
    } catch (Exception e) {
      System.out.println(e.getMessage());
      configBuilder.printUsage();
      return;
    }
    AsyncClient client = new AsyncClient(config);
    client.run();
  }

  private static class HistogramFuture implements Future<Histogram> {
    private final Histogram histogram;
    private boolean canceled;
    private boolean done;

    HistogramFuture(Histogram histogram) {
      Preconditions.checkNotNull(histogram, "histogram");
      this.histogram = histogram;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      if (!done && !canceled) {
        canceled = true;
        notifyAll();
        return true;
      }
      return false;
    }

    @Override
    public synchronized boolean isCancelled() {
      return canceled;
    }

    @Override
    public synchronized boolean isDone() {
      return done || canceled;
    }

    @Override
    public synchronized Histogram get() throws InterruptedException, ExecutionException {
      while (!isDone() && !isCancelled()) {
        wait();
      }

      if (isCancelled()) {
        throw new CancellationException();
      }

      return histogram;
    }

    @Override
    public Histogram get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException,
        TimeoutException {
      throw new UnsupportedOperationException();
    }

    private synchronized void done() {
      done = true;
      notifyAll();
    }
  }
}
