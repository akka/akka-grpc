/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld.grpc;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.Trailers;
import akka.grpc.internal.GrpcMetadataImpl;
import akka.grpc.javadsl.GrpcExceptionHandler;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;


public class RichErrorModelTest extends JUnitSuite {

    private com.google.protobuf.any.Any fromJavaProto(com.google.protobuf.Any javaPbSource) {
        return com.google.protobuf.any.Any.of(javaPbSource.getTypeUrl(), javaPbSource.getValue());
    }

    private CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {

        GreeterService impl = new RichErrorImpl();

        // #custom_eHandler
        Function<ActorSystem, Function<Throwable, Trailers>> customErrorHandler = new Function<ActorSystem, Function<Throwable, Trailers>>() {
            @Override
            public Function<Throwable, Trailers> apply(ActorSystem param) throws Exception, Exception {
                return new Function<Throwable, Trailers>() {
                    @Override
                    public Trailers apply(Throwable ex) throws Exception, Exception {
                        if ((ex instanceof CompletionException)) ex = ex.getCause();
                        if ((ex instanceof io.grpc.StatusRuntimeException)) {
                            StatusRuntimeException statusEx = (StatusRuntimeException) ex;
                            return new Trailers(statusEx.getStatus(), new GrpcMetadataImpl(statusEx.getTrailers()));
                        } else {
                            return GrpcExceptionHandler.defaultMapper().apply(param).apply(ex);
                        }
                    }
                };
            }
        };
        akka.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>> service = GreeterServiceHandlerFactory.create(impl, customErrorHandler, sys);
        // #custom_eHandler

        return Http
                .get(sys)
                .newServerAt("127.0.0.1", 8090)
                .bind(service);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testManualApproach() throws Exception {
        Config conf = ConfigFactory.load();
        ActorSystem sys = ActorSystem.create("HelloWorld", conf);
        run(sys);

        GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8090, sys).withTls(false);

        GreeterServiceClient client = null;
        try {
            client = GreeterServiceClient.create(settings, sys);

            // #client_request
            HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
            CompletionStage<HelloReply> response = client.sayHello(request);
            StatusRuntimeException statusEx = response.toCompletableFuture().handle((res, ex) -> {
                return (StatusRuntimeException) ex;
            }).get();

            com.google.rpc.Status status = StatusProto.fromStatusAndTrailers(statusEx.getStatus(), statusEx.getTrailers());
            example.myapp.helloworld.grpc.helloworld.HelloReply details = fromJavaProto(status.getDetails(0)).unpack(example.myapp.helloworld.grpc.helloworld.HelloReply.messageCompanion());

            assertEquals(Status.INVALID_ARGUMENT.getCode().value(), status.getCode());
            assertEquals("What is wrong?", status.getMessage());
            assertEquals("The password!", details.message());
            // #client_request

        } catch (Exception e) {
            Assert.fail("Got unexpected error " + e.getMessage());
        } finally {
            if (client != null) client.close();
            sys.terminate();
        }
    }
}
