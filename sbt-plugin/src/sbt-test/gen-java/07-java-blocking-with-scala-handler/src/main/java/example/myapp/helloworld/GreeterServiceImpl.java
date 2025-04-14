package example.myapp.helloworld;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import example.myapp.helloworld.grpc.*;

class GreeterServiceImpl implements GreeterService {
  public HelloReply sayHello(HelloRequest request) {
    throw new UnsupportedOperationException();
  }

  public Source<HelloReply, NotUsed> streamHellos(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public HelloReply itKeepsTalking(Source<HelloRequest, NotUsed> in) {
    throw new UnsupportedOperationException();
  }

  public Source<HelloReply, NotUsed> itKeepsReplying(HelloRequest request) {
    throw new UnsupportedOperationException();
  }
}
