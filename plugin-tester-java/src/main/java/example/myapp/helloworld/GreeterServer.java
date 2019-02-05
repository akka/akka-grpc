/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.http.javadsl.*;
import akka.http.javadsl.settings.ServerSettings;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import example.myapp.helloworld.grpc.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

class GreeterServer {
  public static void main(String[] args) throws Exception {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
              .withFallback(ConfigFactory.defaultApplication());

      // Akka ActorSystem Boot
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);

      run(sys).thenAccept(bindings -> {
          bindings.forEach(binding -> {
              System.out.println("gRPC server bound to: " + binding.localAddress());
          });
      });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<List<ServerBinding>> run(ActorSystem sys) throws Exception {
      Materializer mat = ActorMaterializer.create(sys);

      // Instantiate implementation
      GreeterService impl = new GreeterServiceImpl(mat);
      GreeterServicePowerApi impl2 = new GreeterServicePowerApiImpl(mat);

      CompletionStage<ServerBinding> binding1 = Http.get(sys).bindAndHandleAsync(
              GreeterServiceHandlerFactory.create(impl, mat, sys),
              ConnectHttp.toHost("127.0.0.1", 8080, UseHttp2.always()),
              mat);
      CompletionStage<ServerBinding> binding2 = Http.get(sys).bindAndHandleAsync(
              GreeterServicePowerApiHandlerFactory.create(impl2, mat, sys),
              ConnectHttp.toHost("127.0.0.1", 8081, UseHttp2.always()),
              mat);
      return binding1.thenCombine(binding2, (b1, b2) -> Arrays.asList(b1, b2));

      // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}
//#full-server
