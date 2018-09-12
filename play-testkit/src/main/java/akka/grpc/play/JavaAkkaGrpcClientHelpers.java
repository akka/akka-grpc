/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.play;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.grpc.GrpcClientSettings$;

import play.api.test.NewTestServer;
import play.api.test.ServerEndpoint;
import play.api.test.ServerEndpoints;

import javax.net.ssl.SSLContext;

public final class JavaAkkaGrpcClientHelpers {
  private static final GrpcClientSettings$ GrpcClientSettings = GrpcClientSettings$.MODULE$;

  private JavaAkkaGrpcClientHelpers() {}

  public static GrpcClientSettings grpcClientSettings(final NewTestServer testServer) {
    final ServerEndpoint http2Endpoint = unsafeGetHttp2Endpoint(testServer.endpoints());
    return grpcClientSettings(http2Endpoint, testServer.testServer().application().actorSystem());
  }

  public static ServerEndpoint unsafeGetHttp2Endpoint(final ServerEndpoints serverEndpoints) {
    final scala.collection.Traversable<ServerEndpoint> possibleEndpoints =
        serverEndpoints.endpoints().filter(e->e.scheme().equals("https") && e.httpVersions().contains("2"));
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

  public static GrpcClientSettings grpcClientSettings(
      final ServerEndpoint http2Endpoint,
      final ActorSystem actorSystem
  ) {
    final SSLContext sslContext = http2Endpoint.ssl().get().sslContext();
    return GrpcClientSettings
        .connectToServiceAt(http2Endpoint.host(), http2Endpoint.port(), actorSystem)
        .withSSLContext(sslContext);
  }

}
