/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.stream.SystemMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.GreeterServicePowerApi;
import example.myapp.helloworld.grpc.GreeterServicePowerApiHandlerFactory;

import java.util.concurrent.CompletionStage;

class PowerGreeterServer {
  public static void main(String[] args) throws Exception {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
              .withFallback(ConfigFactory.defaultApplication());

      // Akka ActorSystem Boot
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);

      run(sys).thenAccept(binding -> {
        System.out.println("gRPC server bound to: " + binding.localAddress());
      });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
      Materializer mat = SystemMaterializer.get(sys).materializer();

      // Instantiate implementation
      GreeterServicePowerApi impl = new GreeterServicePowerApiImpl(mat);

      return Http
        .get(sys)
        .newServerAt("127.0.0.1", 8091)
        .bind(GreeterServicePowerApiHandlerFactory.create(impl,sys));
  }
}
//#full-server
