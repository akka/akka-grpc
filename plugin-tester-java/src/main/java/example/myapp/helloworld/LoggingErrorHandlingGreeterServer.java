/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.grpc.Trailers;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.Function;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.google.rpc.Code;
import com.google.rpc.LocalizedMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import io.grpc.Status;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static akka.http.javadsl.server.Directives.*;

public class LoggingErrorHandlingGreeterServer {
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

  //#implementation

  private static final RuntimeException nameNonEmptyException = new IllegalArgumentException("Name must be capitalized");
  private static final RuntimeException nameCapitalizedException = new IllegalArgumentException("Name must be capitalized");

  private static class Impl extends GreeterServiceImpl {
    public Impl(Materializer mat) {
      super(mat);
    }
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {
      if (in.getName().isEmpty()) {
        CompletableFuture<HelloReply> reply = new CompletableFuture<>();
        reply.completeExceptionally(nameNonEmptyException);
        return reply;
      } else if (Character.isLowerCase(in.getName().charAt(0))) {
        CompletableFuture<HelloReply> reply = new CompletableFuture<>();
        reply.completeExceptionally(nameCapitalizedException);
        return reply;
      } else {
        HelloReply reply = HelloReply.newBuilder()
          .setMessage("Hello, " + in.getName())
          .build();
        return CompletableFuture.completedFuture(reply);
      }
    }
  }
  //#implementation

  //#method
  private static <ServiceImpl> Route loggingErrorHandlingGrpcRoute(
    Function<RequestContext, ServiceImpl> buildImpl,
    Function<ActorSystem, Function<Throwable, Trailers>> errorHandler,
    BiFunction<ServiceImpl, Function<ActorSystem, Function<Throwable, Trailers>>, akka.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>>> buildHandler
  ) {
    return logRequest("loggingErrorHandlingGrpcRoute", Logging.InfoLevel(), () -> extractRequestContext(ctx -> {
      Function<ActorSystem, Function<Throwable, Trailers>> loggingErrorHandler = (actorSystem) -> (throwable) -> {
        Function<Throwable, Trailers> function = errorHandler.apply(actorSystem);
        Trailers trailers = function.apply(throwable);
        if (trailers != null) {
          ctx.getLog().error(throwable, "Grpc failure handled and mapped to " + trailers);
          return trailers;
        } else {
          Trailers internal = new Trailers(Status.INTERNAL);
          ctx.getLog().error(throwable, "Grpc failure UNHANDLED and mapped to " + internal);
          return internal;
        }
      };
      try {
        ServiceImpl impl = buildImpl.apply(ctx);
        akka.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>> handler = buildHandler.apply(impl, loggingErrorHandler);
        return handle(handler);
      } catch (Exception e) {
        return failWith(e);
      }
    }));
  }
  //#method

  //#custom-error-mapping
  private final static Function<Throwable, Trailers> customErrorMapping = (throwable) -> {
    if (throwable instanceof IllegalArgumentException) {
      if (throwable.getMessage().equals(nameNonEmptyException.getMessage())) {
        // We can pass through the message by attaching it to the Status.
        return new Trailers(Status.INVALID_ARGUMENT.withDescription(throwable.getMessage()));
      } else if (throwable.getMessage().equals(nameCapitalizedException.getMessage())) {
        // We can pass through extra error details like localized versions of the message.
        // FIXME: This subproject uses Java protos, which do not extend GeneratedMessage, so the code below does not compile.
        //   It seems strange that we're requiring a java.util.List[GeneratedMessage] when java protos do not extend the GeneratedMessage trait.
        // com.google.rpc.LocalizedMessage lm = com.google.rpc.LocalizedMessage.newBuilder().setLocale("en-US").setMessage(throwable.getMessage()).build();
        // return Trailers.create(Code.INVALID_ARGUMENT, throwable.getMessage(), List.of(lm));
        return Trailers.create(Code.INVALID_ARGUMENT, throwable.getMessage(), List.of());
      } else {
        return new Trailers(Status.INVALID_ARGUMENT);
      }
    } else {
      return null;
    }
  };
  //#custom-error-mapping

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    Materializer mat = SystemMaterializer.get(sys).materializer();

    //#combined
    Route route = loggingErrorHandlingGrpcRoute(
      (rc) -> new Impl(rc.getMaterializer()),
      (actorSystem) -> customErrorMapping,
      (impl, eHandler) -> GreeterServiceHandlerFactory.partial(impl, GreeterService.name, mat, eHandler, sys)
    );
    return Http.get(sys)
      .newServerAt("127.0.0.1", 8082)
      .bind(route);
    //#combined
  }
}
