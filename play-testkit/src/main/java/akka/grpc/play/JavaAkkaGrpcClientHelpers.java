/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play;

import static scala.compat.java8.JFunction.*;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;

import play.api.test.NewTestServer;
import play.api.test.ServerEndpoint;
import play.api.test.ServerEndpoints;

import javax.net.ssl.SSLContext;

/** Helpers to test Java Akka gRPC clients with Play. */
public final class JavaAkkaGrpcClientHelpers {
  private JavaAkkaGrpcClientHelpers() {}

  /** Creates a GrpcClientSettings from the given NewTestServer. */
  public static GrpcClientSettings grpcClientSettings(final NewTestServer testServer) {
    final ServerEndpoint http2Endpoint = getHttp2Endpoint(testServer.endpoints());
    return grpcClientSettings(http2Endpoint, testServer.testServer().application().actorSystem());
  }

  /**
   * Unsafely gets the HTTP/2 endpoint from the given ServerEndpoints.
   *
   * If no HTTP/2 endpoint exists this throws an IllegalArgumentException.
   */
  public static ServerEndpoint getHttp2Endpoint(final ServerEndpoints serverEndpoints) {
    final scala.collection.Traversable<ServerEndpoint> possibleEndpoints =
        serverEndpoints.endpoints().filter(func(e->e.httpVersions().contains("2")));
    if (possibleEndpoints.size() != 1) {
      throw new IllegalArgumentException(String.format(
          "gRPC client can't automatically find HTTP/2 connection: " +
              "%s valid endpoints available: %s",
          possibleEndpoints.size(),
          serverEndpoints
      ));
    }
    return possibleEndpoints.head();
  }

  /** Creates a GrpcClientSettings from the given HTTP/2 endpoint and ActorSystem. */
  public static GrpcClientSettings grpcClientSettings(
      final ServerEndpoint http2Endpoint,
      final ActorSystem actorSystem
  ) {

    final ServerEndpoint.ClientSsl clientSsl = http2Endpoint.ssl().getOrElse(func(() -> {
      throw new IllegalArgumentException(
          "GrpcClientSettings requires a server endpoint with ssl, but non provided");
    }));

    return grpcClientSettings(http2Endpoint, clientSsl.sslContext(), actorSystem);
  }

  public static GrpcClientSettings grpcClientSettings(
      final ServerEndpoint http2Endpoint,
      final SSLContext sslContext,
      final ActorSystem actorSystem
  ) {
    return GrpcClientSettings
        .connectToServiceAt(http2Endpoint.host(), http2Endpoint.port(), actorSystem)
        .withSSLContext(sslContext);
  }

}
