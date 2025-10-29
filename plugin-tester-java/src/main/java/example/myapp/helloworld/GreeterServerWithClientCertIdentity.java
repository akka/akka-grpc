/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;

import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

class GreeterServerWithClientCertIdentity {
  public static void main(String[] args) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("akka.http.server.enable-http2 = on")
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

    //#with-mtls-cert-identity
    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);
    Function<HttpRequest, CompletionStage<HttpResponse>> handler = GreeterServiceHandlerFactory.create(impl, sys);

    // As a Route
    Route handlerRoute = handle(handler);
    //#grpc-route

    //#grpc-protected
    // Protect the handler route
    Route protectedHandler =
      requireClientCertificateIdentity("mycorp\\..*\\.client\\d+}", () -> {
        return handlerRoute;

      });

    return Http.get(sys)
      .newServerAt("127.0.0.1", 8090)
      .bind(protectedHandler);
    //#with-mtls-cert-identity
  }
}
