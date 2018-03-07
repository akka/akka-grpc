package io.grpc.examples;

import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.*;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.*;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.http.javadsl.Http;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.HttpsConnectionContext;

class Main {
  public static void main(String[] args) throws Exception {
    ActorSystem sys = ActorSystem.create();
    Materializer mat = ActorMaterializer.create(sys);

    GreeterService impl = new GreeterServiceImpl();

    Http.get(sys).bindAndHandleAsync(
      GreeterServiceHandlerFactory.create(impl, mat),
      ConnectWithHttps.toHostHttps("127.0.0.1", 8080).withCustomHttpsContext(serverHttpContext()),
      mat);
  }

  private static HttpsConnectionContext serverHttpContext() throws Exception {
    // never put passwords into code!
    char[] password = "abcdef".toCharArray();

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(Main.class.getClassLoader().getResourceAsStream("server.p12"), password);

    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
    keyManagerFactory.init(ks, password);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

    return ConnectionContext.https(context);
  }
}