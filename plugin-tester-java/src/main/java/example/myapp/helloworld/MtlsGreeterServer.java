/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;

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
    try {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

      // keyStore is for the server cert and private key
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null);
      PrivateKey serverPrivateKey =
        DERPrivateKeyLoader.load(PEMDecoder.decode(classPathFileAsString("/certs/localhost-server.key")));
      Certificate serverCert = certFactory.generateCertificate(
        MtlsGreeterServer.class.getResourceAsStream("/certs/localhost-server.crt"));
      keyStore.setKeyEntry(
        "private",
        serverPrivateKey,
        // No password for our private key
        new char[0],
        new Certificate[]{ serverCert });
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
      keyManagerFactory.init(keyStore, null);
      final KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

      // trustStore is for what client certs the server trust
      KeyStore trustStore = KeyStore.getInstance("PKCS12");
      trustStore.load(null);
      // any client cert signed by this CA is allowed to connect
      trustStore.setEntry(
        "rootCA",
        new KeyStore.TrustedCertificateEntry(
          certFactory.generateCertificate(MtlsGreeterServer.class.getResourceAsStream("/certs/rootCA.crt"))),
        null);
      /*
      // or specific client certs (less likely to be useful)
      trustStore.setEntry(
        "client1",
        new KeyStore.TrustedCertificateEntry(
          certFactory.generateCertificate(getClass().getResourceAsStream("/certs/localhost-client.crt"))),
        null)
       */
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(trustStore);
      final TrustManager[] trustManagers = tmf.getTrustManagers();

      HttpsConnectionContext httpsContext = ConnectionContext.httpsServer(() -> {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, new SecureRandom());

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);

        // require client certs
        engine.setNeedClientAuth(true);

        return engine;
      });
      return httpsContext;

    } catch (Exception ex) {
      throw new RuntimeException("Failed setting up the server HTTPS context", ex);
    }
  }

  private static String classPathFileAsString(String path) {
    try {
      BufferedInputStream in = new BufferedInputStream(MtlsGreeterServer.class.getResourceAsStream(path));
      ByteArrayOutputStream bao = new ByteArrayOutputStream();
      for (int result = in.read(); result != -1; result = in.read()) {
        bao.write((byte) result);
      }
      return bao.toString("UTF-8");
    } catch (Exception ex) {
      throw new RuntimeException("Failed reading server key from classpath", ex);
    }
  }

}
