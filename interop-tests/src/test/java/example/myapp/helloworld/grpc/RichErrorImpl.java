package example.myapp.helloworld.grpc;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.google.protobuf.any.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RichErrorImpl implements GreeterService {

    private com.google.protobuf.Any toJavaProto(com.google.protobuf.any.Any scalaPbSource) {
        com.google.protobuf.Any.Builder javaPbOut = com.google.protobuf.Any.newBuilder();
        javaPbOut.setTypeUrl(scalaPbSource.typeUrl());
        javaPbOut.setValue(scalaPbSource.value());
        return javaPbOut.build();
    }

    // #rich_error_model_unary
    @Override
    public CompletionStage<HelloReply> sayHello(HelloRequest in) {
        Status status = Status.newBuilder()
                .setCode(Code.INVALID_ARGUMENT_VALUE)
                .setMessage("What is wrong?")
                .addDetails(toJavaProto(Any.pack(
                        example.myapp.helloworld.grpc.helloworld.HelloReply.of("The password!")
                )))
                .build();
        StatusRuntimeException statusRuntimeException = StatusProto.toStatusRuntimeException(status);

        CompletableFuture<HelloReply> future = new CompletableFuture<>();
        future.completeExceptionally(statusRuntimeException);
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
