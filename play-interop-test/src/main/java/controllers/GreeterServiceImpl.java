package controllers;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import java.util.concurrent.CompletionStage;

public class GreeterServiceImpl implements GreeterService {

  @Override
  public CompletionStage<HelloReply> sayHello(HelloRequest in) {
    return null;
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
