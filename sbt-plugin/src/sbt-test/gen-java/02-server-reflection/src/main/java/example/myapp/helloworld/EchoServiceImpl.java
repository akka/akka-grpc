package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import example.myapp.helloworld.grpc.*;

class EchoServiceImpl implements EchoService {
  public CompletionStage<HelloRequest> echo(HelloRequest request) {
    return CompletableFuture.completedFuture(request);
  }
}
