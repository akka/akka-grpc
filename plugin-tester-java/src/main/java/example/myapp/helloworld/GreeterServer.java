/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
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

class GreeterServer {
  public static void main(String[] args) throws Exception {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());

      // Akka ActorSystem Boot
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);
      Materializer mat = ActorMaterializer.create(sys);

      // Instantiate implementation
      GreeterService impl = new GreeterServiceImpl(mat);

      // Bind implementation to localhost:8080
      Http.get(sys).bindAndHandleAsync(
          GreeterServiceHandlerFactory.create(impl, mat),
          ConnectHttp.toHost("127.0.0.1", 8080, UseHttp2.always()),
          ServerSettings.create(sys),
          // Needed to allow running multiple requests concurrently, see https://github.com/akka/akka-http/issues/2145
          256,
          sys.log(),
          mat)
      .thenAccept(binding -> {
        System.out.println("gRPC server bound to: " + binding.localAddress());
      });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}
//#full-server
