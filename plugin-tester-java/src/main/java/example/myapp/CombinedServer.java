/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.HttpsConnectionContext;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
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
import akka.grpc.javadsl.ServiceHandler;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;

//#import

import example.myapp.helloworld.*;
import example.myapp.helloworld.grpc.*;
import example.myapp.echo.*;
import example.myapp.echo.grpc.*;

class CombinedServer {
  public static void main(String[] args) throws Exception {
      // important to enable HTTP/2 in ActorSystem's config
      Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication());
      ActorSystem sys = ActorSystem.create("HelloWorld", conf);
      Materializer mat = ActorMaterializer.create(sys);

      //#concatOrNotFound
      Function<HttpRequest, CompletionStage<HttpResponse>> greeterService =
          GreeterServiceHandlerFactory.create(new GreeterServiceImpl(mat), mat);
      Function<HttpRequest, CompletionStage<HttpResponse>> echoService =
        EchoServiceHandlerFactory.create(new EchoServiceImpl(), mat);
      Function<HttpRequest, CompletionStage<HttpResponse>> serviceHandlers =
        ServiceHandler.concatOrNotFound(greeterService, echoService);

      Http.get(sys).bindAndHandleAsync(
          serviceHandlers,
          ConnectWithHttps.toHostHttps("127.0.0.1", 8080).withCustomHttpsContext(serverHttpContext()),
          mat)
      //#concatOrNotFound
      .thenAccept(binding -> {
        System.out.println("gRPC server bound to: " + binding.localAddress());
      });
  }

  private static HttpsConnectionContext serverHttpContext() throws Exception {
    // FIXME how would end users do this? TestUtils.loadCert? issue #89
    String keyEncoded = read(CombinedServer.class.getResourceAsStream("/certs/server1.key"))
        .replace("-----BEGIN PRIVATE KEY-----\n", "")
        .replace("-----END PRIVATE KEY-----\n", "")
        .replace("\n", "");

    byte[] decodedKey = Base64.getDecoder().decode(keyEncoded);

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);

    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey privateKey = kf.generatePrivate(spec);

    CertificateFactory fact = CertificateFactory.getInstance("X.509");
    Certificate cer = fact.generateCertificate(CombinedServer.class.getResourceAsStream("/certs/server1.pem"));

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null);
    ks.setKeyEntry("private", privateKey, new char[0], new Certificate[]{cer});

    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
    keyManagerFactory.init(ks, null);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

    return ConnectionContext.https(context);
  }

  private static String read(InputStream in) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, in.available()));
    byte[] buffer = new byte[32 * 1024];
    int bytesRead = in.read(buffer);
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead);
      bytesRead = in.read(buffer);
    }

    byte[] bytes = baos.toByteArray();
    return new String(bytes, "UTF-8");
  }
}
