/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.NotUsed;
import akka.grpc.GrpcServiceException;
import akka.stream.javadsl.Source;
import com.google.api.HttpBody;
import com.google.protobuf.Message;
import com.google.rpc.Code;
import com.google.rpc.LocalizedMessage;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RichErrorNativeImpl implements GreeterService {

    // #rich_error_model_unary
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {

        ArrayList<Message> ar = new ArrayList<>();
        ar.add(LocalizedMessage.newBuilder().setLocale("EN").setMessage("The password!").build());

        GrpcServiceException exception = GrpcServiceException.create(
                Code.INVALID_ARGUMENT,
                "What is wrong?",
                ar
        );

        CompletableFuture<HelloReply> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        return future;
    }
    // #rich_error_model_unary


    @Override
    public CompletionStage<HttpBody> sayHelloHttp(HelloRequest in) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
        return null;
    }

    @Override
    public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
        return null;
    }

    @Override
    public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
        return null;
    }
}
