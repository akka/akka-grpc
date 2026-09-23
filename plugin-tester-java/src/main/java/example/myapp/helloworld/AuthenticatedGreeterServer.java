/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.ServerInterceptor;
import akka.grpc.javadsl.ServerInterceptors;
import akka.grpc.javadsl.ServerReflection;
import akka.grpc.javadsl.ServiceHandler;
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
import io.grpc.Status;

import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;

import static akka.http.javadsl.server.Directives.*;

class AuthenticatedGreeterServer {
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

    //#http-route
    // A Route to authenticate with
    Route authentication = path("login", () ->
      get(() ->
        complete("Psst, please use token XYZ!")
      )
    );
    //#http-route

    //#interceptor
    // An interceptor that rejects calls without the right token
    ServerInterceptor requireToken = (serviceName, methodName, request, next) -> {
      boolean validToken = request.getHeader("token").map(header -> header.value().equals("XYZ")).orElse(false);
      if (validToken) {
        return next.apply(request);
      } else {
        CompletableFuture<HttpResponse> rejected = new CompletableFuture<>();
        rejected.completeExceptionally(new GrpcServiceException(
            Status.UNAUTHENTICATED.withDescription("Missing or invalid token for " + serviceName + "/" + methodName)));
        return rejected;
      }
    };
    //#interceptor

    //#grpc-protected
    // Create service handlers, the interceptor only applies to the greeter service
    GreeterService impl = new GreeterServiceImpl(mat);
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
      ServiceHandler.concatOrNotFound(
        ServerInterceptors.intercept(
          GreeterService.description,
          GreeterServiceHandlerFactory.create(impl, sys),
          sys,
          requireToken),
        ServerReflection.create(Collections.singletonList(GreeterService.description), sys));

    // As a Route
    Route handlerRoute = handle(handler);
    //#grpc-protected

    //#combined
    Route finalRoute = concat(
      authentication,
      handlerRoute
    );

    return Http.get(sys)
      .newServerAt("127.0.0.1", 8090)
      .bind(finalRoute);
    //#combined
  }
}
