package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import akka.NotUsed;
import akka.stream.javadsl.Source;
import example.myapp.helloworld.grpc.*;

class GreeterServiceImpl implements GreeterService {
  public CompletionStage<HelloReply> sayHello(HelloRequest request) {
    return CompletableFuture.completedFuture(HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
  }

  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public CompletionStage<HelloReply> itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest request) {
    throw new UnsupportedOperationException();
  }
}
