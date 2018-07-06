/*
 * Copyright (C) 2018 Lightbend Inc. <http://www.lightbend.com>
 * Copyright 2015, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.grpc.benchmarks;

import akka.grpc.GrpcClientSettings;
import akka.grpc.benchmarks.proto.Control;
import akka.grpc.benchmarks.proto.Messages;
import akka.grpc.benchmarks.proto.Messages.Payload;
import akka.grpc.benchmarks.proto.Messages.SimpleRequest;
import akka.grpc.benchmarks.proto.Messages.SimpleResponse;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.HttpsConnectionContext;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.internal.testing.TestUtils;
import org.HdrHistogram.Histogram;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
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

/**
 * Utility methods to support benchmarking classes.
 */
public final class Utils {

  // The histogram can record values between 1 microsecond and 1 min.
  public static final long HISTOGRAM_MAX_VALUE = 60000000L;

  // Value quantization will be no more than 1%. See the README of HdrHistogram for more details.
  public static final int HISTOGRAM_PRECISION = 2;

  private Utils() {
  }

  public static boolean parseBoolean(String value) {
    return value.isEmpty() || Boolean.parseBoolean(value);
  }

  /**
   * Parse a {@link SocketAddress} from the given string.
   */
  public static SocketAddress parseSocketAddress(String value) {
    // Standard TCP/IP address.
    String[] parts = value.split(":", 2);
    if (parts.length < 2) {
      throw new IllegalArgumentException(
          "Address must be a unix:// path or be in the form host:port. Got: " + value);
    }
    String host = parts[0];
    int port = Integer.parseInt(parts[1]);
    return new InetSocketAddress(host, port);
  }


  /**
   * Save a {@link Histogram} to a file.
   */
  public static void saveHistogram(Histogram histogram, String filename) throws IOException {
    File file;
    PrintStream log = null;
    try {
      file = new File(filename);
      if (file.exists() && !file.delete()) {
        System.err.println("Failed deleting previous histogram file: " + file.getAbsolutePath());
      }
      log = new PrintStream(new FileOutputStream(file), false);
      histogram.outputPercentileDistribution(log, 1.0);
    } finally {
      if (log != null) {
        log.close();
      }
    }
  }

  /**
   * Construct a {@link SimpleResponse} for the given request.
   */
  public static SimpleResponse makeResponse(SimpleRequest request) {
    if (request.getResponseSize() > 0) {
      if (!Messages.PayloadType.COMPRESSABLE.equals(request.getResponseType())) {
        throw Status.INTERNAL.augmentDescription("Error creating payload.").asRuntimeException();
      }

      ByteString body = ByteString.copyFrom(new byte[request.getResponseSize()]);
      Messages.PayloadType type = request.getResponseType();

      Payload payload = Payload.newBuilder().setType(type).setBody(body).build();
      return SimpleResponse.newBuilder().setPayload(payload).build();
    }
    return SimpleResponse.getDefaultInstance();
  }

  /**
   * Construct a {@link SimpleRequest} with the specified dimensions.
   */
  public static SimpleRequest makeRequest(Messages.PayloadType payloadType, int reqLength,
                                          int respLength) {
    ByteString body = ByteString.copyFrom(new byte[reqLength]);
    Payload payload = Payload.newBuilder()
        .setType(payloadType)
        .setBody(body)
        .build();

    return SimpleRequest.newBuilder()
        .setResponseType(payloadType)
        .setResponseSize(respLength)
        .setPayload(payload)
        .build();
  }

  /**
   * Picks a port that is not used right at this moment.
   * Warning: Not thread safe. May see "BindException: Address already in use: bind" if using the
   * returned port to create a new server socket when other threads/processes are concurrently
   * creating new sockets without a specific port.
   */
  public static int pickUnusedPort() {
    try {
      ServerSocket serverSocket = new ServerSocket(0);
      int port = serverSocket.getLocalPort();
      serverSocket.close();
      return port;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static HttpsConnectionContext serverHttpContext() throws Exception {
    String keyEncoded = new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath())), "UTF-8")
        .replace("-----BEGIN PRIVATE KEY-----\n", "")
        .replace("-----END PRIVATE KEY-----\n", "")
        .replace("\n", "");

    byte[] decodedKey = Base64.getDecoder().decode(keyEncoded);

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);

    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey privateKey = kf.generatePrivate(spec);

    CertificateFactory fact = CertificateFactory.getInstance("X.509");
    FileInputStream is = new FileInputStream(TestUtils.loadCert("server1.pem"));
    Certificate cer = fact.generateCertificate(is);

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null);
    ks.setKeyEntry("private", privateKey, new char[0], new Certificate[]{cer});

    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
    keyManagerFactory.init(ks, null);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

    return ConnectionContext.https(context);
  }

  public static GrpcClientSettings createGrpcClientSettings(InetSocketAddress socketAddress, boolean useTls) {
    GrpcClientSettings settings = GrpcClientSettings.create(socketAddress.getHostName(), socketAddress.getPort());
    if (useTls) // having security params means --tls
      // Note: In this sample we are using a dummy TLS cert so we need to fake the authority
      return settings
          .withOverrideAuthority(TestUtils.TEST_SERVER_HOST)
          .withTrustedCaCertificate("ca.pem");
    else
      return settings;

  }
}
