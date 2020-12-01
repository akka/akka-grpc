/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;

import akka.http.javadsl.model.StatusCodes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.SystemMaterializer;
import akka.stream.Materializer;

import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;

import static akka.http.javadsl.server.Directives.*;

class AuthenticatedGreeterServer {
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

    //#http-route
    // A Route to authenticate with
    Route authentication = path("login", () ->
      get(() ->
        complete("Psst, please use token XYZ!")
      )
    );
    //#http-route

    //#grpc-route
    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);
    Function<HttpRequest, CompletionStage<HttpResponse>> handler = GreeterServiceHandlerFactory.create(impl, sys);

    // As a Route
    Route handlerRoute = handle(handler);
    //#grpc-route

    //#grpc-protected
    // Protect the handler route
    Route protectedHandler =
      headerValueByName("token", token -> {
        if ("XYZ".equals(token)) {
          return handlerRoute;
        } else {
          return complete(StatusCodes.UNAUTHORIZED);
        }
      });
    //#grpc-protected

    //#combined
    Route finalRoute = concat(
      authentication,
      protectedHandler
    );

    return Http.get(sys)
      .newServerAt("127.0.0.1", 8090)
      .bind(finalRoute);
    //#combined
  }
}
