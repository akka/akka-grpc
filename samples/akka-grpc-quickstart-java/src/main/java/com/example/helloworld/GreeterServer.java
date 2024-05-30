package com.example.helloworld;

//#import

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.*;
import akka.http.javadsl.common.SSLContextFactory;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletionStage;

//#import

//#server
public class GreeterServer {

  public static void main(String[] args) throws Exception {
    // important to enable HTTP/2 in ActorSystem's config
    Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.load());
    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "GreeterServer", conf);
    new GreeterServer(system).run();
  }

  final ActorSystem<?> system;

  public GreeterServer(ActorSystem<?> system) {
    this.system = system;
  }

  public CompletionStage<ServerBinding> run() throws Exception {

    Function<HttpRequest, CompletionStage<HttpResponse>> service =
        GreeterServiceHandlerFactory.create(
            new GreeterServiceImpl(system),
            system);

    HttpsConnectionContext httpsConnectionContext = ConnectionContext.httpsServer(SSLContextFactory.createSSLContextFromPem(
            // Note: filesystem paths, not classpath
            Paths.get("src/main/resources/certs/server1.pem"),
            Paths.get("src/main/resources/certs/server1.key")
    ));

    CompletionStage<ServerBinding> bound =
            Http.get(system)
                    .newServerAt("127.0.0.1", 8080)
                    .enableHttps(httpsConnectionContext)
                    .bind(service);

    bound.thenAccept(binding ->
        System.out.println("gRPC server bound to: " + binding.localAddress())
    );

    return bound;
  }
}
//#server
