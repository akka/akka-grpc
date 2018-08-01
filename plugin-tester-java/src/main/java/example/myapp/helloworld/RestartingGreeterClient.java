package example.myapp.helloworld;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.javadsl.RestartingClient;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import example.myapp.helloworld.grpc.*;

class RestartingGreeterClient {
    public static void main(String[] args) throws Exception {

        String serverHost = "127.0.0.1";
        int serverPort = 8080;

        ActorSystem system = ActorSystem.create();
        Materializer materializer = ActorMaterializer.create(system);

        try {
            //#restarting-client
            GrpcClientSettings settings = GrpcClientSettings.fromConfig(GreeterService.name, system);

            RestartingClient<GreeterServiceClient> client = new RestartingClient<>(
                    () -> GreeterServiceClient.create(settings, materializer, system.dispatcher()), system.dispatcher()
            );
            //#restarting-client

            singleRequestReply(client);
            streamingRequest(client);
            streamingReply(client, materializer);
            streamingRequestReply(client, materializer);

            client.close();


        } finally {
            system.terminate();
        }

    }

    private static void singleRequestReply(RestartingClient<GreeterServiceClient> client) throws Exception {
        //#usage
        HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
        CompletionStage<HelloReply> reply = client.withClient(c -> c.sayHello(request));
        System.out.println("got single reply: " + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
        //#usage
    }

    private static void streamingRequest(RestartingClient<GreeterServiceClient> client) throws Exception {
        List<HelloRequest> requests = Stream.of("Alice", "Bob", "Peter")
                .map(name -> HelloRequest.newBuilder().setName(name).build())
                .collect(Collectors.toList());
        CompletionStage<HelloReply> reply = client.withClient(c -> c.itKeepsTalking(Source.from(requests)));
        System.out.println("got single reply for streaming requests: " +
                reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    private static void streamingReply(RestartingClient<GreeterServiceClient> client, Materializer mat) throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
        Source<HelloReply, NotUsed> responseStream = client.withClient(c -> c.itKeepsReplying(request));
        CompletionStage<Done> done =
                responseStream.runForeach(reply ->
                        System.out.println("got streaming reply: " + reply.getMessage()), mat);

        done.toCompletableFuture().get(60, TimeUnit.SECONDS);
    }

    private static void streamingRequestReply(RestartingClient<GreeterServiceClient> client, Materializer mat) throws Exception {
        Duration interval = Duration.ofSeconds(1);
        Source<HelloRequest, NotUsed> requestStream = Source
                .tick(interval, interval, "tick")
                .zipWithIndex()
                .map(Pair::second)
                .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
                .take(10)
                .mapMaterializedValue(m -> NotUsed.getInstance());

        Source<HelloReply, NotUsed> responseStream = client.withClient(c -> c.streamHellos(requestStream));
        CompletionStage<Done> done =
                responseStream.runForeach(reply ->
                        System.out.println("got streaming reply: " + reply.getMessage()), mat);

        done.toCompletableFuture().get(60, TimeUnit.SECONDS);
    }

}
