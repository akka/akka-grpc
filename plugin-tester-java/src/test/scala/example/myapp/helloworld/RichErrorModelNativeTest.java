/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.MetadataStatus;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import com.google.rpc.LocalizedMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import example.myapp.helloworld.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;


public class RichErrorModelNativeTest extends JUnitSuite {

    private ServerBinding run(ActorSystem sys) throws Exception {

        GreeterService impl = new RichErrorNativeImpl();

        akka.japi.function.Function<HttpRequest, CompletionStage<HttpResponse>> service = GreeterServiceHandlerFactory.create(impl, sys);
        CompletionStage<ServerBinding> bound = Http
                .get(sys)
                .newServerAt("127.0.0.1", 8091)
                .bind(service);

        bound.thenAccept(binding -> {
            System.out.println("gRPC server bound to: " + binding.localAddress());
        });
        return bound.toCompletableFuture().get();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNativeApi() throws Exception {
        Config conf = ConfigFactory.load();
        ActorSystem sys = ActorSystem.create("HelloWorld", conf);
        run(sys);

        GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8091, sys).withTls(false);

        GreeterServiceClient client = null;
        try {
            client = GreeterServiceClient.create(settings, sys);

            // #client_request
            HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
            CompletionStage<HelloReply> response = client.sayHello(request);
            StatusRuntimeException statusRuntimeException = response.toCompletableFuture().handle((res, ex) -> {
                return (StatusRuntimeException) ex;
            }).get();

            GrpcServiceException ex = GrpcServiceException.apply(statusRuntimeException);
            MetadataStatus meta = (MetadataStatus) ex.getMetadata();
            assertEquals("type.googleapis.com/google.rpc.LocalizedMessage", meta.getDetails().get(0).typeUrl());

            assertEquals(Status.INVALID_ARGUMENT.getCode().value(), meta.getCode());
            assertEquals("What is wrong?", meta.getMessage());

            LocalizedMessage details = meta.getParsedDetails(LocalizedMessage.getDefaultInstance()).get(0);
            Assert.assertEquals("The password!", details.getMessage());
            Assert.assertEquals("EN", details.getLocale());
            // #client_request

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Got unexpected error " + e.getMessage());
        } finally {
            if (client != null) client.close();
            sys.terminate();
        }
    }
}
