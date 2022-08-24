/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld.grpc;

import akka.NotUsed;
import akka.grpc.GrpcServiceException;
import akka.stream.javadsl.Source;
import com.google.rpc.Code;
import com.google.rpc.error_details.LocalizedMessage;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RichErrorNativeImpl implements GreeterService {

    // #rich_error_model_unary
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {

        ArrayList<scalapb.GeneratedMessage> ar = new ArrayList<>();
        ar.add(LocalizedMessage.of("EN", "The password!"));

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
