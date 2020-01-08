package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorSystem;
import akka.grpc.javadsl.ServiceHandler;
import akka.grpc.javadsl.ServerReflection;
import akka.http.javadsl.*;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import example.myapp.helloworld.grpc.*;

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
        Materializer mat = ActorMaterializer.create(sys);

        // Instantiate implementation
        GreeterService impl = new GreeterServiceImpl();

        return Http.get(sys).bindAndHandleAsync(
            ServiceHandler.concatOrNotFound(
                GreeterServiceHandlerFactory.create(impl, mat, sys),
                new ServerReflection(java.util.Arrays.asList(GreeterService.service)).create(mat, sys)
            ),
            ConnectHttp.toHost("127.0.0.1", 8080),
            mat);
    }

}