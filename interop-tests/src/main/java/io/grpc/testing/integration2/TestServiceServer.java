/*
 * Copyright 2014, gRPC Authors All rights reserved.
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

package io.grpc.testing.integration2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.testing.integration.AbstractInteropTest;
import io.grpc.testing.integration.TestServiceImpl;
import io.netty.handler.ssl.SslContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server that manages startup/shutdown of a single {@code TestService}.
 */
public class TestServiceServer {
  /**
   * The main application allowing this server to be launched from the command line.
   */
  public static void main(String[] args) throws Exception {
    final TestServiceServer server = new TestServiceServer();
    server.parseArgs(args);
    if (server.useTls) {
      System.out.println(
          "\nUsing fake CA for TLS certificate. Test clients should expect host\n"
          + "*.test.google.fr and our test CA. For the Java test client binary, use:\n"
          + "--server_host_override=foo.test.google.fr --use_test_ca=true\n");
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          System.out.println("Shutting down");
          server.stop();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    server.start();
    System.out.println("Server started on port " + server.port);
    server.blockUntilShutdown();
  }

  public int port = 8080;
  public boolean useTls = true;

  private ScheduledExecutorService executor;
  private Server server;

  @VisibleForTesting
  public void parseArgs(String[] args) {
    boolean usage = false;
    for (String arg : args) {
      if (!arg.startsWith("--")) {
        System.err.println("All arguments must start with '--': " + arg);
        usage = true;
        break;
      }
      String[] parts = arg.substring(2).split("=", 2);
      String key = parts[0];
      if ("help".equals(key)) {
        usage = true;
        break;
      }
      if (parts.length != 2) {
        System.err.println("All arguments must be of the form --arg=value");
        usage = true;
        break;
      }
      String value = parts[1];
      if ("port".equals(key)) {
        port = Integer.parseInt(value);
      } else if ("use_tls".equals(key)) {
        useTls = Boolean.parseBoolean(value);
      } else if ("grpc_version".equals(key)) {
        if (!"2".equals(value)) {
          System.err.println("Only grpc version 2 is supported");
          usage = true;
          break;
        }
      } else {
        System.err.println("Unknown argument: " + key);
        usage = true;
        break;
      }
    }
    if (usage) {
      TestServiceServer s = new TestServiceServer();
      System.out.println(
          "Usage: [ARGS...]"
          + "\n"
          + "\n  --port=PORT           Port to connect to. Default " + s.port
          + "\n  --use_tls=true|false  Whether to use TLS. Default " + s.useTls
      );
      System.exit(1);
    }
  }

  @VisibleForTesting
  public void start() throws Exception {
    executor = Executors.newSingleThreadScheduledExecutor();
    SslContext sslContext = null;
    if (useTls) {
      sslContext = GrpcSslContexts.forServer(
              TestUtils.loadCert("server1.pem"), TestUtils.loadCert("server1.key")).build();
    }
    server = NettyServerBuilder.forPort(port)
        .sslContext(sslContext)
        .maxInboundMessageSize(AbstractInteropTest.MAX_MESSAGE_SIZE)
        .addService(ServerInterceptors.intercept(
            new TestServiceImpl(executor),
            TestServiceImpl.interceptors()))
        .build().start();
  }

  @VisibleForTesting
  public void stop() throws Exception {
    server.shutdownNow();
    if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
      System.err.println("Timed out waiting for server shutdown");
    }
      System.out.println("Server stopped");
    MoreExecutors.shutdownAndAwaitTermination(executor, 5, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  int getPort() {
    return server.getPort();
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }
}
