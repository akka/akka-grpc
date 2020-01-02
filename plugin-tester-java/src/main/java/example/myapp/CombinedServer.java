/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp;

import akka.actor.ActorSystem;
import akka.http.javadsl.*;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.concurrent.CompletionStage;

//#import
import akka.grpc.javadsl.ServiceHandler;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;

//#import

import example.myapp.helloworld.*;
import example.myapp.helloworld.grpc.*;
import example.myapp.echo.*;
import example.myapp.echo.grpc.*;

class CombinedServer {
  public static void main(String[] args) throws Exception {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);
      Materializer mat = ActorMaterializer.create(sys);

      //#concatOrNotFound
      Function<HttpRequest, CompletionStage<HttpResponse>> greeterService =
          GreeterServiceHandlerFactory.create(new GreeterServiceImpl(mat), mat, sys);
      Function<HttpRequest, CompletionStage<HttpResponse>> echoService =
        EchoServiceHandlerFactory.create(new EchoServiceImpl(), mat, sys);
      Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers =
        ServiceHandler.concatOrNotFound(greeterService, echoService);

      Http.get(sys).bindAndHandleAsync(
          serviceHandlers,
          ConnectHttp.toHost("127.0.0.1", 8080),
          mat)
      //#concatOrNotFound
      .thenAccept(binding -> {
        System.out.println("gRPC server bound to: " + binding.localAddress());
      });
  }
}
