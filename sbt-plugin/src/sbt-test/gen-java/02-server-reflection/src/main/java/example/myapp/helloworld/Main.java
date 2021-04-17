package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;
import akka.japi.function.Function;

import akka.actor.ActorSystem;

import akka.stream.SystemMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

//#server-reflection
import java.util.Arrays;

import akka.grpc.javadsl.ServiceHandler;
import akka.grpc.javadsl.ServerReflection;
import akka.http.javadsl.*;
import akka.http.javadsl.model.*;

import example.myapp.helloworld.grpc.*;

//#server-reflection

public class Main {
    public static void main(String[] args) throws Exception {
        // important to enable HTTP/2 in ActorSystem's config
        Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
            .withFallback(ConfigFactory.defaultApplication());
        // Akka ActorSystem Boot
        ActorSystem sys = ActorSystem.create("HelloWorld", conf);

        run(sys).thenAccept(binding -> {
            System.out.println("gRPC server bound to: " + binding.localAddress());
        });
    }

    public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
        Materializer mat = SystemMaterializer.get(sys).materializer();

        //#server-reflection-manual-concat
        // Create service handlers
        Function<HttpRequest, CompletionStage<HttpResponse>> greetingPartial =
                GreeterServiceHandlerFactory.create(new GreeterServiceImpl(), "greeting-prefix", sys);
        Function<HttpRequest, CompletionStage<HttpResponse>> echoPartial =
                EchoServiceHandlerFactory.create(new EchoServiceImpl(), sys);
        // Create the reflection handler for multiple services
        Function<HttpRequest, CompletionStage<HttpResponse>> reflectionPartial =
                ServerReflection.create(Arrays.asList(GreeterService.description, EchoService.description), sys);

        // Concatenate the partial functions into a single handler
        ServiceHandler.concatOrNotFound(
                                greetingPartial,
                                echoPartial,
                                reflectionPartial);
        //#server-reflection-manual-concat

        //#server-reflection
        // Instantiate implementation
        GreeterService impl = new GreeterServiceImpl();

        // Bind service handler servers to localhost:8080
        return Http.get(sys)
          .newServerAt("127.0.0.1", 8080)
          .bind(GreeterServiceHandlerFactory.createWithServerReflection(impl, sys));
        //#server-reflection
    }

}
