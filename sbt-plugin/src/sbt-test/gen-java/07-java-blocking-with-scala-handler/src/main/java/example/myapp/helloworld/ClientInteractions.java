package example.myapp.helloworld;

import example.myapp.helloworld.grpc.*;
import akka.stream.javadsl.Source;
import java.util.concurrent.CompletionStage;

class ClientInteractions {
    void compileOnlyChecks() {
        GreeterServiceClient client = null;

        HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("Blocking Bob").build());

        HelloReply reply2 = client.sayHello().invoke(HelloRequest.newBuilder().setName("Blocking Bob").build());
        CompletionStage<HelloReply> asyncReply1 = client.sayHello().invokeAsync(HelloRequest.newBuilder().setName("Blocking Bob").build());

        HelloReply reply3 = client.itKeepsTalking(Source.single(HelloRequest.newBuilder().setName("Blocking Bob").build()));

        HelloReply reply4 = client.itKeepsTalking().invoke(Source.single(HelloRequest.newBuilder().setName("Blocking Bob").build()));
        CompletionStage<HelloReply> asyncReply2 = client.itKeepsTalking().invokeAsync(Source.single(HelloRequest.newBuilder().setName("Blocking Bob").build()));
    }
}