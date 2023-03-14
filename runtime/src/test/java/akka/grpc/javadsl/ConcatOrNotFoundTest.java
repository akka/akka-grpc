/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl;

import akka.grpc.internal.GrpcProtocolNative;
import akka.http.javadsl.model.*;
import akka.util.ByteString;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ConcatOrNotFoundTest extends JUnitSuite {

  private final HttpRequest emptyGrpcRequest =
      HttpRequest.create().withEntity(GrpcProtocolNative.contentType(), ByteString.emptyByteString());

  private final CompletionStage<HttpResponse> notFound = CompletableFuture.completedFuture(
      HttpResponse.create().withStatus(StatusCodes.NOT_FOUND));

  private CompletionStage<HttpResponse> response(int code) {
    return CompletableFuture.completedFuture(HttpResponse.create().withStatus(StatusCodes.custom(
        code, "reason-" + code, "message-" + code)));
  }

  @Test
  @SuppressWarnings("unchecked") // unchecked generic array creation
  public void testSingleMatching() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> response(231)
    ).apply(emptyGrpcRequest);

    assertEquals(231, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testFirstMatching() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> response(231),
        req -> notFound
    ).apply(emptyGrpcRequest);

    assertEquals(231, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testMiddleMatching() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> notFound,
        req -> response(232),
        req -> notFound
    ).apply(emptyGrpcRequest);

    assertEquals(232, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testLastMatching() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> notFound,
        req -> notFound,
        req -> response(233)
        ).apply(emptyGrpcRequest);

    assertEquals(233, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testNoneMatching() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> notFound,
        req -> notFound,
        req -> notFound
    ).apply(emptyGrpcRequest);

    assertEquals(404, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testEmpty() throws Exception {
    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound().apply(emptyGrpcRequest);

    assertEquals(404, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testCompletedLater() throws Exception {
    CompletableFuture<HttpResponse> laterNotFound = new CompletableFuture<>();
    CompletableFuture<HttpResponse> laterResponse = new CompletableFuture<>();

    CompletionStage<HttpResponse> response = ServiceHandler.concatOrNotFound(
        req -> laterNotFound,
        req -> laterResponse,
        req -> notFound
    ).apply(emptyGrpcRequest);

    Thread.sleep(100);
    laterNotFound.complete(notFound.toCompletableFuture().get());

    Thread.sleep(100);
    laterResponse.complete(response(232).toCompletableFuture().get());

    assertEquals(232, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status().intValue());
  }

}
