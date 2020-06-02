/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
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

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.*;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.sun.management.OperatingSystemMXBean;
import io.grpc.Status;
import io.grpc.netty.shaded.io.netty.util.concurrent.DefaultThreadFactory;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.LogarithmicIterator;
import org.HdrHistogram.Recorder;
import org.apache.commons.math3.distribution.ExponentialDistribution;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the client-side contract for the load testing scenarios.
 */
class LoadClient {

  private static final Logger log = Logger.getLogger(LoadClient.class.getName());

  private final Control.ClientConfig config;
  private final ExponentialDistribution distribution;
  private volatile boolean shutdown;
  private final int threadCount;

  private final ActorSystem system;
  private final Materializer mat;

  private final BenchmarkServiceClient[] clients;
  private final Recorder recorder;
  private final ExecutorService fixedThreadPool;
  private final Messages.SimpleRequest simpleRequest;
  private final OperatingSystemMXBean osBean;
  private long lastMarkCpuTime;

  LoadClient(Control.ClientConfig config) throws Exception {
    log.log(Level.INFO, "Client Config \n" + config.toString());
    this.config = config;

    this.system = ActorSystem.create("AsyncClient");
    this.mat = Materializer.create(system);

    // Create the clients
    clients = new BenchmarkServiceClient[config.getClientChannels()];
    for (int i = 0; i < config.getClientChannels(); i++) {
      InetSocketAddress socketAddress = (InetSocketAddress) Utils.parseSocketAddress(
          config.getServerTargets(i % config.getServerTargetsCount()));

      GrpcClientSettings settings = Utils.createGrpcClientSettings(socketAddress, config.hasSecurityParams(), system);

      BenchmarkServiceClient client = BenchmarkServiceClient.create(settings, system);

      clients[i] = client;
    }

    // Determine no of threads
    threadCount = config.getAsyncClientThreads() == 0
        ? Runtime.getRuntime().availableProcessors()
        : config.getAsyncClientThreads();

    // Use a fixed sized pool of daemon threads.
    fixedThreadPool = Executors.newFixedThreadPool(threadCount,
        new DefaultThreadFactory("client-worker", true));

    system.getWhenTerminated().whenComplete((__, t) -> shutdownNow());

    // Create the load distribution
    switch (config.getLoadParams().getLoadCase()) {
      case CLOSED_LOOP:
        distribution = null;
        break;
      case LOAD_NOT_SET:
        distribution = null;
        break;
      case POISSON:
        // Mean of exp distribution per thread is <no threads> / <offered load per second>
        distribution = new ExponentialDistribution(
            threadCount / config.getLoadParams().getPoisson().getOfferedLoad());
        break;
      default:
        throw new IllegalArgumentException("Scenario not implemented");
    }

    // Create payloads
    switch (config.getPayloadConfig().getPayloadCase()) {
      case SIMPLE_PARAMS: {
        Payloads.SimpleProtoParams simpleParams = config.getPayloadConfig().getSimpleParams();
        simpleRequest = Utils.makeRequest(Messages.PayloadType.COMPRESSABLE,
            simpleParams.getReqSize(), simpleParams.getRespSize());
        break;
      }
      default: {
        // Not implemented yet
        throw new IllegalArgumentException("Scenario not implemented");
      }
    }

    List<OperatingSystemMXBean> beans =
        ManagementFactory.getPlatformMXBeans(OperatingSystemMXBean.class);
    if (!beans.isEmpty()) {
      osBean = beans.get(0);
    } else {
      osBean = null;
    }

    // Create the histogram recorder
    recorder = new Recorder((long) config.getHistogramParams().getMaxPossible(), 3);
  }

  /**
   * Start the load scenario.
   */
  void start() {
    for (int i = 0; i < threadCount; i++) {
      Runnable r = null;
      switch (config.getPayloadConfig().getPayloadCase()) {
        case SIMPLE_PARAMS: {
          if (config.getClientType() == Control.ClientType.ASYNC_CLIENT) {
            if (config.getRpcType() == Control.RpcType.UNARY) {
              r = new AsyncUnaryWorker(clients[i % clients.length]);
            } else if (config.getRpcType() == Control.RpcType.STREAMING) {
              r = new AsyncPingPongWorker(mat, clients[i % clients.length]);
            }
          }
          break;
        }
        default: {
          throw Status.UNIMPLEMENTED.withDescription(
              "Unknown payload case " + config.getPayloadConfig().getPayloadCase().name())
              .asRuntimeException();
        }
      }
      if (r == null) {
        throw new IllegalStateException(config.getRpcType().name()
            + " not supported for client type "
            + config.getClientType());
      }
      fixedThreadPool.execute(r);
    }
    if (osBean != null) {
      lastMarkCpuTime = osBean.getProcessCpuTime();
    }
  }

  /**
   * Take a snapshot of the statistics which can be returned to the driver.
   */
  Stats.ClientStats getStats() {
    Histogram intervalHistogram = recorder.getIntervalHistogram();

    Stats.ClientStats.Builder statsBuilder = Stats.ClientStats.newBuilder();
    Stats.HistogramData.Builder latenciesBuilder = statsBuilder.getLatenciesBuilder();
    double resolution = 1.0 + Math.max(config.getHistogramParams().getResolution(), 0.01);
    LogarithmicIterator logIterator = new LogarithmicIterator(intervalHistogram, 1,
        resolution);
    double base = 1;
    while (logIterator.hasNext()) {
      latenciesBuilder.addBucket((int) logIterator.next().getCountAddedInThisIterationStep());
      base = base * resolution;
    }
    // Driver expects values for all buckets in the range, not just the range of buckets that
    // have values.
    while (base < config.getHistogramParams().getMaxPossible()) {
      latenciesBuilder.addBucket(0);
      base = base * resolution;
    }
    latenciesBuilder.setMaxSeen(intervalHistogram.getMaxValue());
    latenciesBuilder.setMinSeen(intervalHistogram.getMinNonZeroValue());
    latenciesBuilder.setCount(intervalHistogram.getTotalCount());
    latenciesBuilder.setSum(intervalHistogram.getMean()
        * intervalHistogram.getTotalCount());
    // TODO: No support for sum of squares

    statsBuilder.setTimeElapsed((intervalHistogram.getEndTimeStamp()
        - intervalHistogram.getStartTimeStamp()) / 1000.0);
    if (osBean != null) {
      // Report all the CPU time as user-time  (which is intentionally incorrect)
      long nowCpu = osBean.getProcessCpuTime();
      statsBuilder.setTimeUser(((double) nowCpu - lastMarkCpuTime) / 1000000000.0);
      lastMarkCpuTime = nowCpu;
    }
    return statsBuilder.build();
  }

  /**
   * Shutdown the scenario as cleanly as possible.
   */
  void shutdownNow() {
    shutdown = true;
    for (int i = 0; i < clients.length; i++) {
      clients[i].close();
    }
    fixedThreadPool.shutdownNow();
    system.terminate();
  }

  /**
   * Record the event elapsed time to the histogram and delay initiation of the next event based
   * on the load distribution.
   */
  void delay(long alreadyElapsed) {
    recorder.recordValue(alreadyElapsed);
    if (distribution != null) {
      long nextPermitted = Math.round(distribution.sample() * 1000000000.0);
      if (nextPermitted > alreadyElapsed) {
        LockSupport.parkNanos(nextPermitted - alreadyElapsed);
      }
    }
  }

  /**
   * Worker which executes async unary calls. Event timing is the duration between sending the
   * request and receiving the response.
   */
  private class AsyncUnaryWorker implements Runnable {
    final BenchmarkServiceClient client;
    final Semaphore maxOutstanding = new Semaphore(config.getOutstandingRpcsPerChannel());

    AsyncUnaryWorker(BenchmarkServiceClient client) {
      this.client = client;
    }

    @Override
    public void run() {
      while (true) {
        maxOutstanding.acquireUninterruptibly();
        if (shutdown) {
          maxOutstanding.release();
          return;
        }
        final long requestTime = System.nanoTime();
        CompletionStage<Messages.SimpleResponse> response = client.unaryCall(simpleRequest);
        response.whenComplete((rsp, t) -> {
          if (t == null) {
            delay(System.nanoTime() - requestTime);
            maxOutstanding.release();
          } else {
            maxOutstanding.release();
            Level level = shutdown ? Level.FINE : Level.INFO;
            log.log(level, "Error in AsyncUnary call", t);
          }
        });
      }
    }
  }

  /**
   * Worker which executes a streaming ping-pong call. Event timing is the duration between
   * sending the ping and receiving the pong.
   */
  private class AsyncPingPongWorker implements Runnable {
    final Materializer mat;
    final BenchmarkServiceClient stub;
    final Semaphore maxOutstanding = new Semaphore(config.getOutstandingRpcsPerChannel());

    AsyncPingPongWorker(Materializer mat, BenchmarkServiceClient stub) {
      this.mat = mat;
      this.stub = stub;
    }

    @Override
    public void run() {
      while (!shutdown) {
        maxOutstanding.acquireUninterruptibly();
        if (!shutdown) {

          final AtomicLong lastCall = new AtomicLong();
          lastCall.set(System.nanoTime());
          final AtomicReference<ActorRef> requestIngress = new AtomicReference<>();
          Source<Messages.SimpleRequest, NotUsed> requestSource =
              Source.<Messages.SimpleRequest>actorRef(2, OverflowStrategy.fail())
                  .mapMaterializedValue(ref -> {
                    requestIngress.set(ref);
                    requestIngress.get().tell(simpleRequest, ActorRef.noSender());
                    return NotUsed.getInstance();
                  });
          CompletionStage<Done> done = stub.streamingCall(requestSource)
              .runWith(Sink.foreach(rsp -> {
                delay(System.nanoTime() - lastCall.get());
                if (shutdown) {
                  requestIngress.get().tell(new akka.actor.Status.Success("done"), ActorRef.noSender());
                  // Must not send another request.
                  return;
                }
                requestIngress.get().tell(simpleRequest, ActorRef.noSender());
                lastCall.set(System.nanoTime());
              }), mat);

          done.whenComplete((d, t) -> {
            if (t == null) {
              maxOutstanding.release();
            } else {
              maxOutstanding.release();
              Level level = shutdown ? Level.FINE : Level.INFO;
              log.log(level, "Error in Async Ping-Pong call", t);
            }
          });
        }
      }
    }
  }


}
