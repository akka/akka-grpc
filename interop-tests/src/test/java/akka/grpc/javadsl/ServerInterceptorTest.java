/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl;

import akka.actor.ActorSystem;
import akka.grpc.GrpcServiceException;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.japi.function.Function;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import io.grpc.Status;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class ServerInterceptorTest extends JUnitSuite {

  private static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("ServerInterceptorTest");
  }

  @AfterClass
  public static void teardown() {
    system.terminate();
  }

  private static final ServerInterceptor rejectAll =
      (service, method, request, next) -> {
        CompletableFuture<HttpResponse> rejected = new CompletableFuture<>();
        rejected.completeExceptionally(new GrpcServiceException(Status.UNAUTHENTICATED));
        return rejected;
      };

  private static final GreeterService impl =
      new GreeterService() {
        @Override
        public CompletionStage<HelloReply> sayHello(HelloRequest in) {
          return CompletableFuture.completedFuture(
              HelloReply.newBuilder().setMessage("Hello, " + in.getName()).build());
        }

        @Override
        public CompletionStage<HelloReply> itKeepsTalking(
            akka.stream.javadsl.Source<HelloRequest, akka.NotUsed> in) {
          throw new UnsupportedOperationException();
        }

        @Override
        public akka.stream.javadsl.Source<HelloReply, akka.NotUsed> itKeepsReplying(HelloRequest in) {
          throw new UnsupportedOperationException();
        }

        @Override
        public akka.stream.javadsl.Source<HelloReply, akka.NotUsed> streamHellos(
            akka.stream.javadsl.Source<HelloRequest, akka.NotUsed> in) {
          throw new UnsupportedOperationException();
        }
      };

  private static HttpRequest sayHello(String service) {
    // one uncompressed, zero length frame: an empty HelloRequest
    byte[] emptyMessageFrame = new byte[] {0, 0, 0, 0, 0};
    return HttpRequest.create("https://localhost/" + service + "/SayHello")
        .withMethod(HttpMethods.POST)
        .withEntity(HttpEntities.create(ContentTypes.parse("application/grpc"), emptyMessageFrame));
  }

  private static Optional<String> grpcStatus(HttpResponse response) {
    return response.getHeader("grpc-status").map(HttpHeader::value);
  }

  private static HttpResponse call(
      Function<HttpRequest, CompletionStage<HttpResponse>> handler, HttpRequest request) throws Exception {
    return handler.apply(request).toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void perServiceInterceptorRejectsOwnServiceOnly() throws Exception {
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        ServerInterceptors.intercept(
            GreeterService.description,
            GreeterServiceHandlerFactory.create(impl, system),
            system,
            rejectAll);

    HttpResponse rejected = call(handler, sayHello(GreeterService.name));
    assertEquals(StatusCodes.OK, rejected.status());
    assertEquals(
        Optional.of(String.valueOf(Status.Code.UNAUTHENTICATED.value())), grpcStatus(rejected));

    HttpResponse other = call(handler, sayHello("other.Service"));
    assertEquals(StatusCodes.NOT_FOUND, other.status());
  }

  @Test
  public void interceptorForAllServicesRejectsEverything() throws Exception {
    // #all-services
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        ServerInterceptors.intercept(
            ServiceHandler.concatOrNotFound(
                GreeterServiceHandlerFactory.create(impl, system),
                ServerReflection.create(Collections.singletonList(GreeterService.description), system)),
            system,
            rejectAll);
    // #all-services

    HttpResponse other = call(handler, sayHello("other.Service"));
    assertEquals(
        Optional.of(String.valueOf(Status.Code.UNAUTHENTICATED.value())), grpcStatus(other));
  }

  @Test
  public void interceptorSeesServiceAndMethodAndPassesOn() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    ServerInterceptor recording =
        (service, method, request, next) -> {
          seen.set(service + "/" + method);
          return next.apply(request);
        };
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        ServerInterceptors.intercept(
            GreeterService.description,
            GreeterServiceHandlerFactory.create(impl, system),
            system,
            recording);

    HttpResponse response = call(handler, sayHello(GreeterService.name));
    assertEquals(StatusCodes.OK, response.status());
    assertEquals(Optional.empty(), grpcStatus(response));
    assertEquals(GreeterService.name + "/SayHello", seen.get());
  }
}
