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

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.Control;
import akka.grpc.benchmarks.proto.Control.ClientArgs;
import akka.grpc.benchmarks.proto.Control.ServerArgs;
import akka.grpc.benchmarks.proto.Control.ServerArgs.ArgtypeCase;
import akka.grpc.benchmarks.proto.WorkerService;
import akka.grpc.benchmarks.proto.WorkerServiceHandlerFactory;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.stream.Materializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A load worker process which a driver can use to create clients and servers. The worker
 * implements the contract defined in 'control.proto'.
 */
public class LoadWorker {

  private static final Logger log = Logger.getLogger(LoadWorker.class.getName());

  private final ActorSystem system;
  private final int driverPort;
  private final int serverPort;

  LoadWorker(ActorSystem system, int driverPort, int serverPort) throws Exception {
    this.system = system;
    this.driverPort = driverPort;
    this.serverPort = serverPort;
  }

  public CompletionStage<ServerBinding> start() throws Exception {
    Materializer mat = Materializer.matFromSystem(system);

    WorkerServiceImpl impl = new WorkerServiceImpl(mat);

    CompletionStage<ServerBinding> bound = Http.get(system).bindAndHandleAsync(
        WorkerServiceHandlerFactory.create(impl, mat, system),
        ConnectWithHttps.toHostHttps("0.0.0.0", driverPort).withCustomHttpsContext(Utils.serverHttpContext()),
        mat);


        bound.thenAccept(binding -> {
          System.out.println("gRPC server bound to: " + binding.localAddress());
        });

        return bound;
  }



  /**
   * Start the load worker process.
   */
  public static void main(String[] args) throws Exception {
    boolean usage = false;
    int serverPort = 0;
    int driverPort = 0;
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
      if ("server_port".equals(key)) {
        serverPort = Integer.valueOf(value);
      } else if ("driver_port".equals(key)) {
        driverPort = Integer.valueOf(value);
      } else {
        System.err.println("Unknown argument: " + key);
        usage = true;
        break;
      }
    }
    if (usage || driverPort == 0) {
      System.err.println(
          "Usage: [ARGS...]"
              + "\n"
              + "\n  --driver_port=<port>"
              + "\n    Port to expose grpc.testing.WorkerService, used by driver to initiate work."
              + "\n  --server_port=<port>"
              + "\n    Port to start load servers on. Defaults to any available port");
      System.exit(1);
    }


    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());
    ActorSystem system = ActorSystem.create("LoadWorker", conf);
    new LoadWorker(system, driverPort, serverPort).start();
  }

  /**
   * Implement the worker service contract which can launch clients and servers.
   */
  private class WorkerServiceImpl implements WorkerService {

    private final Materializer mat;

    private LoadServer workerServer;
    private LoadClient workerClient;

    private WorkerServiceImpl(Materializer mat) {
      this.mat = mat;
    }

    @Override
    public Source<Control.ServerStatus, NotUsed> runServer(Source<ServerArgs, NotUsed> in) {
      return in.map(value -> {
        try {
          ArgtypeCase argTypeCase = value.getArgtypeCase();
          if (argTypeCase == ServerArgs.ArgtypeCase.SETUP && workerServer == null) {
            if (serverPort != 0 && value.getSetup().getPort() == 0) {
              Control.ServerArgs.Builder builder = value.toBuilder();
              builder.getSetupBuilder().setPort(serverPort);
              value = builder.build();
            }
            workerServer = new LoadServer(value.getSetup());
            workerServer.start();
            return Control.ServerStatus.newBuilder()
                .setPort(workerServer.getPort())
                .setCores(workerServer.getCores())
                .build();
          } else if (argTypeCase == ArgtypeCase.MARK && workerServer != null) {
            return Control.ServerStatus.newBuilder()
                .setStats(workerServer.getStats())
                .build();
          } else {
            throw Status.ALREADY_EXISTS
                .withDescription("Server already started")
                .asRuntimeException();
          }
        } catch (Throwable t) {
          log.log(Level.WARNING, "Error running server", t);
          if (!(t instanceof StatusRuntimeException) && !(t instanceof StatusException))
            throw Status.INTERNAL.withCause(t).asException();
          else
            throw t;
          // FIXME: Shutdown server if we can
        }
      });

      // FIXME should also shutdown workerServer if client (upstream of in) completes, see onComplete in original code
    }

    @Override
    public Source<Control.ClientStatus, NotUsed> runClient(Source<ClientArgs, NotUsed> in) {
      return in.map(value -> {
        try {
          ClientArgs.ArgtypeCase argTypeCase = value.getArgtypeCase();
          if (argTypeCase == ClientArgs.ArgtypeCase.SETUP && workerClient == null) {
            workerClient = new LoadClient(value.getSetup());
            workerClient.start();
            return Control.ClientStatus.newBuilder().build();
          } else if (argTypeCase == ClientArgs.ArgtypeCase.MARK && workerClient != null) {
            return Control.ClientStatus.newBuilder()
                .setStats(workerClient.getStats())
                .build();
          } else {
            throw Status.ALREADY_EXISTS
                .withDescription("Client already started")
                .asRuntimeException();
          }
        } catch (Throwable t) {
          log.log(Level.WARNING, "Error running client", t);
          if (!(t instanceof StatusRuntimeException) && !(t instanceof StatusException))
            throw Status.INTERNAL.withCause(t).asException();
          else
            throw t;
          // FIXME Shutdown the client if we can
        }
      });
    }

    @Override
    public CompletionStage<Control.CoreResponse> coreCount(Control.CoreRequest in) {
      return CompletableFuture.completedFuture(
          Control.CoreResponse.newBuilder()
              .setCores(Runtime.getRuntime().availableProcessors())
              .build());
    }

    @Override
    public CompletionStage<Control.Void> quitWorker(Control.Void in) {
      log.log(Level.INFO, "Received quitWorker request.");
      system.scheduler().scheduleOnce(Duration.ofSeconds(3), () -> {
        log.log(Level.INFO, "DriverServer has terminated.");
        // Allow enough time for quitWorker to deliver OK status to the driver.
        system.terminate();
      }, system.dispatcher());
      return CompletableFuture.completedFuture(Control.Void.getDefaultInstance());
    }
  }

}
