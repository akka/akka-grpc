/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.helloworld;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.pki.pem.DERPrivateKeyLoader;
import akka.pki.pem.PEMDecoder;
import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;

import javax.net.ssl.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletionStage;

public class MtlsGreeterClient {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("MtlsHelloWorldClient");

    GrpcClientSettings clientSettings =
      GrpcClientSettings.connectToServiceAt("localhost", 8443, system)
        .withSslContext(sslContext());

    GreeterServiceClient client = GreeterServiceClient.create(clientSettings, system);

    CompletionStage<HelloReply> reply = client.sayHello(HelloRequest.newBuilder().setName("Jonas").build());

    reply.whenComplete((response, error) -> {
      if (error == null) {
        System.out.println("Successful reply: " + reply);
      } else {
        System.out.println("Request failed");
        error.printStackTrace();
      }
      system.terminate();
    });
  }

  private static SSLContext sslContext() {
    try {
      PrivateKey clientPrivateKey =
        DERPrivateKeyLoader.load(PEMDecoder.decode(classPathFileAsString("/certs/client1.key")));
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

      // keyStore is for the client cert and private key
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null);
      Certificate clientCertificate = certFactory.generateCertificate(MtlsGreeterClient.class.getResourceAsStream("/certs/client1.crt"));
      keyStore.setKeyEntry(
        "private",
        clientPrivateKey,
        // No password for our private client key
        new char[0],
        new Certificate[]{clientCertificate});
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
      keyManagerFactory.init(keyStore, null);
      KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

      // trustStore is for what server certs the client trust
      KeyStore trustStore = KeyStore.getInstance("PKCS12");
      trustStore.load(null);
      // accept any server cert signed by this CA
      trustStore.setEntry(
        "rootCA",
        new KeyStore.TrustedCertificateEntry(
          certFactory.generateCertificate(MtlsGreeterClient.class.getResourceAsStream("/certs/rootCA.crt"))),
        null);
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(trustStore);
      TrustManager[] trustManagers = tmf.getTrustManagers();

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(keyManagers, trustManagers, new SecureRandom());
      return context;
    } catch (Exception ex) {
      throw new RuntimeException("Failed to set up SSL context for the client", ex);
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
