/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-server
package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.common.SSLContextFactory;
import akka.http.javadsl.model.AttributeKeys;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.SslSessionInfo;
import akka.japi.function.Function;
import akka.pki.pem.DERPrivateKeyLoader;
import akka.pki.pem.PEMDecoder;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import example.myapp.helloworld.grpc.GreeterService;
import example.myapp.helloworld.grpc.GreeterServiceHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

class MtlsGreeterServer {

  private static final Logger log = LoggerFactory.getLogger(MtlsGreeterServer.class);

  public static void main(String[] args) throws Exception {
    ActorSystem sys = ActorSystem.create("MtlsHelloWorldServer");

    run(sys).thenAccept(binding -> {
      log.info("gRPC server bound to {}", binding.localAddress());
    });

    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }

  public static CompletionStage<ServerBinding> run(ActorSystem sys) throws Exception {
    Materializer mat = SystemMaterializer.get(sys).materializer();

    // Instantiate implementation
    GreeterService impl = new GreeterServiceImpl(mat);

    Function<HttpRequest, CompletionStage<HttpResponse>> service =
      GreeterServiceHandlerFactory.create(impl, sys);

    return Http
      .get(sys)
      .newServerAt("127.0.0.1", 8443)
      .enableHttps(serverHttpContext())
      .bind(service);
  }

  private static HttpsConnectionContext serverHttpContext() {
    return ConnectionContext.httpsServer(() -> {
      SSLContext context = SSLContextFactory.createSSLContextFromPem(
        // Note: these are filesystem paths, not classpath
        Paths.get("src/main/resources/certs/localhost-server.crt"),
        Paths.get("src/main/resources/certs/localhost-server.key"),
        // client certs to trust are issued by this CA
        List.of(Paths.get("src/main/resources/certs/rootCA.crt")));

      SSLEngine engine = context.createSSLEngine();
      engine.setUseClientMode(false);

      // require client certs
      engine.setNeedClientAuth(true);
      return engine;
    });
  }
}
//#full-server
