/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.shelf;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.shelf.grpc.ShelfService;
import example.myapp.shelf.grpc.ShelfServiceHandlerFactory;

import java.util.concurrent.CompletionStage;

public class ShelfServer {
  public static void main(String[] args) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("akka.http.server.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication());

    // Akka ActorSystem Boot
    ActorSystem sys = ActorSystem.create("ShelfServer", conf);

    run(sys).thenAccept(binding -> System.out.println("gRPC HTTP transcoding server bound to: " + binding.localAddress()));

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    ShelfService impl = new ShelfServiceImpl();

    Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers = ShelfServiceHandlerFactory.createWithHttpTranscoding(impl, sys);

    return Http
      .get(sys)
      .newServerAt("127.0.0.1", 8080)
      .bind(serviceHandlers);
  }
}
