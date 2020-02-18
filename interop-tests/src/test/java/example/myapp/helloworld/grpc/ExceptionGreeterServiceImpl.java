package example.myapp.helloworld.grpc;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import io.grpc.Status;

import akka.NotUsed;
import akka.grpc.GrpcServiceException;
import akka.stream.javadsl.Source;

public class ExceptionGreeterServiceImpl implements GreeterService {
    //#unary
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {
        if (in.getName().isEmpty()) {
            CompletableFuture<HelloReply> future = new CompletableFuture<>();
            future.completeExceptionally(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found")));
            return future;
        } else {
            return CompletableFuture.completedFuture(HelloReply.newBuilder().setMessage("Hi, " + in.getName()).build());
        }
    }
    //#unary

    private Source<HelloReply, NotUsed> myResponseSource = null;
    
    //#streaming
    @Override
    public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest in) {
      if (in.getName().isEmpty()) {
            return Source.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found")));
        } else {
            return myResponseSource;
        }
    }
    //#streaming

    @Override
    public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest,NotUsed> in) {
        return null;
    }

    @Override
    public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
        return null;
    }
    
}