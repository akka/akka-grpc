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

import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.Control;
import akka.grpc.benchmarks.proto.Stats;
import akka.grpc.benchmarks.qps.AsyncServer;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the server-side contract for the load testing scenarios.
 */
final class LoadServer {

  private static final Logger log = Logger.getLogger(LoadServer.class.getName());

  private AsyncServer server;
  private final OperatingSystemMXBean osBean;
  private final int port;
  private long lastStatTime;
  private long lastMarkCpuTime;

  LoadServer(Control.ServerConfig config) throws Exception {
    log.log(Level.INFO, "Server Config \n" + config.toString());
    port = config.getPort() ==  0 ? Utils.pickUnusedPort() : config.getPort();

    switch (config.getServerType()) {
      case ASYNC_SERVER: {
        break;
      }
      case SYNC_SERVER: {
        throw new IllegalArgumentException("SYNC_SERVER not implemented");
      }
      case ASYNC_GENERIC_SERVER: {
        throw new IllegalArgumentException("ASYNC_GENERIC_SERVER not implemented");
      }
      default: {
        throw new IllegalArgumentException();
      }
    }
    if (config.hasSecurityParams()) {
      // FIXME currently only Utils.serverHttpContext and the test certs are used
    }

    List<OperatingSystemMXBean> beans =
        ManagementFactory.getPlatformMXBeans(OperatingSystemMXBean.class);
    if (!beans.isEmpty()) {
      osBean = beans.get(0);
    } else {
      osBean = null;
    }
  }

  int getPort() {
    return port;
  }

  int getCores() {
    return Runtime.getRuntime().availableProcessors();
  }

  void start() throws Exception {

    InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);

    server = new AsyncServer();
    server.run(address, true);

    lastStatTime = System.nanoTime();
    if (osBean != null) {
      lastMarkCpuTime = osBean.getProcessCpuTime();
    }
  }

  Stats.ServerStats getStats() {
    Stats.ServerStats.Builder builder = Stats.ServerStats.newBuilder();
    long now = System.nanoTime();
    double elapsed = ((double) now - lastStatTime) / 1000000000.0;
    lastStatTime = now;
    builder.setTimeElapsed(elapsed);
    if (osBean != null) {
      // Report all the CPU time as user-time  (which is intentionally incorrect)
      long nowCpu = osBean.getProcessCpuTime();
      builder.setTimeUser(((double) nowCpu - lastMarkCpuTime) / 1000000000.0);
      lastMarkCpuTime = nowCpu;
    }
    return builder.build();
  }

  void shutdownNow() {
    if (server != null)
      server.shutdown();
  }

}
