package example.myapp.helloworld;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorSystem;

import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

//#server-reflection
import java.util.Arrays;

import akka.grpc.javadsl.ServiceHandler;
import akka.grpc.javadsl.ServerReflection;
import akka.http.javadsl.*;

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
        Materializer mat = ActorMaterializer.create(sys);

        //#server-reflection
        // Instantiate implementation
        GreeterService impl = new GreeterServiceImpl();

        // Bind service handler servers to localhost:8080
        return Http.get(sys).bindAndHandleAsync(
            ServiceHandler.concatOrNotFound(
                GreeterServiceHandlerFactory.create(impl, mat, sys),
                ServerReflection.create(Arrays.asList(GreeterService.description), mat, sys)
            ),
            ConnectHttp.toHost("127.0.0.1", 8080),
            mat);
        //#server-reflection
    }

}