/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.interop;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.*;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.*;

import akka.actor.ActorSystem;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.*;
import akka.http.javadsl.settings.ServerSettings;
import akka.japi.Function;
import akka.stream.*;
import com.typesafe.config.ConfigFactory;
import io.grpc.internal.testing.TestUtils;
import io.grpc.testing.integration.TestService;
import scala.Function2;
import scala.Tuple2;

/**
 * Glue code to start a gRPC server based on the akka-grpc Java API to test against
 */
public class AkkaGrpcServerJava extends GrpcServer<Tuple2<ActorSystem, ServerBinding>> {
  private final Function2<Materializer, ActorSystem, Function<HttpRequest, CompletionStage<HttpResponse>>> handlerFactory;

  public AkkaGrpcServerJava(Function2<Materializer, ActorSystem, Function<HttpRequest, CompletionStage<HttpResponse>>> handlerFactory) {
    this.handlerFactory = handlerFactory;
  }

  public Tuple2<ActorSystem, ServerBinding> start() throws Exception {
    ActorSystem sys = ActorSystem.create(
      "akka-grpc-server-java",
      ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on"));
    Materializer mat = ActorMaterializer.create(sys);

    Function<HttpRequest, CompletionStage<HttpResponse>> testService = handlerFactory.apply(mat, sys);

    CompletionStage<ServerBinding> binding = Http.get(sys).bindAndHandleAsync(
      req -> {
        Iterator<String> segmentIterator = req.getUri().pathSegments().iterator();
        if (segmentIterator.hasNext()) {
          if (segmentIterator.next().equals(TestService.name)) {
            return testService.apply(req);
          } else {
            return CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND));
          }
        } else {
          return CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.NOT_FOUND));
        }
      },
      ConnectWithHttps.toHostHttps("127.0.0.1", 0).withCustomHttpsContext(serverHttpContext()),
      ServerSettings.create(sys),
      256, // parallelism TODO remove once https://github.com/akka/akka-http/pull/2146 is merged
      sys.log(),
      mat);

    ServerBinding serverBinding = binding.toCompletableFuture().get();
    return new Tuple2<>(sys, serverBinding);
  }

  @Override
  public int getPort(Tuple2<ActorSystem, ServerBinding> binding) {
    return binding._2.localAddress().getPort();
  }

  public void stop(Tuple2<ActorSystem, ServerBinding> binding) throws Exception {
    binding._2.unbind().toCompletableFuture().get();
    binding._1.terminate();
    binding._1.getWhenTerminated().toCompletableFuture().get();
  }

  private HttpsConnectionContext serverHttpContext() throws Exception {
    String keyEncoded = new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath())), "UTF-8")
      .replace("-----BEGIN PRIVATE KEY-----\n", "")
      .replace("-----END PRIVATE KEY-----\n", "")
      .replace("\n", "");

    byte[] decodedKey = Base64.getDecoder().decode(keyEncoded);

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);

    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey privateKey = kf.generatePrivate(spec);

    CertificateFactory fact = CertificateFactory.getInstance("X.509");
    InputStream is = new FileInputStream(TestUtils.loadCert("server1.pem"));
    Certificate cer = fact.generateCertificate(is);

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null);
    ks.setKeyEntry("private", privateKey, new char[]{}, new Certificate[] { cer });

    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
    keyManagerFactory.init(ks, null);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

    return HttpsConnectionContext.https(context);
  }

}
