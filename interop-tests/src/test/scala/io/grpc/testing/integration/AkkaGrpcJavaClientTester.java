/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package io.grpc.testing.integration;

import akka.grpc.GrpcClientSettings;
import akka.grpc.GrpcResponseMetadata;
import akka.grpc.GrpcSingleResponse;
import akka.grpc.javadsl.Metadata;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.testing.integration.test.SSLContextUtils;
import io.grpc.testing.integration2.ClientTester;
import io.grpc.testing.integration2.Settings;
import scala.concurrent.ExecutionContext;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AkkaGrpcJavaClientTester implements ClientTester {
  private final Settings settings;
  private final Materializer mat;
  private final ExecutionContext ec;

  private TestServiceClient client;
  private UnimplementedServiceClient clientUnimplementedService;

  public AkkaGrpcJavaClientTester(Settings settings, Materializer mat, ExecutionContext ec) {
    this.settings = settings;
    this.mat = mat;
    this.ec = ec;
  }

  @Override
  public void setUp() {
    final GrpcClientSettings grpcSettings =
        GrpcClientSettings.create(settings.serverHost(), settings.serverPort())
          .withOverrideAuthority(settings.serverHostOverride())
          .withSSLContext(SSLContextUtils.sslContextForCert("ca.pem"));
    client = TestServiceClient.create(grpcSettings, mat, ec);
    clientUnimplementedService = UnimplementedServiceClient.create(grpcSettings, mat, ec);
  }

  @Override
  public void tearDown() throws Exception {
    if (client != null) client.close();
    if (clientUnimplementedService != null) clientUnimplementedService.close();
  }

  @Override
  public void emptyUnary() throws Exception {
    assertEquals(
      EmptyProtos.Empty.newBuilder().build(),
      client.emptyCall(EmptyProtos.Empty.newBuilder().build()).toCompletableFuture().get()
    );
  }

  @Override
  public void cacheableUnary() {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void largeUnary() throws Exception {
    final Messages.SimpleRequest request = Messages.SimpleRequest.newBuilder()
      .setResponseSize(314159)
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[271828])))
      .build();

    final Messages.SimpleResponse expectedResponse = Messages.SimpleResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[314159])))
      .build();

    final Messages.SimpleResponse response = client.unaryCall(request).toCompletableFuture().get();
    assertEquals(expectedResponse, response);
  }

  @Override
  public void clientCompressedUnary(boolean probe) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void serverCompressedUnary() throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void clientStreaming() throws Exception {
    final List<Messages.StreamingInputCallRequest> requests = new ArrayList<>();
    requests.add(Messages.StreamingInputCallRequest.newBuilder().setPayload(
      Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[27182]))).build());
    requests.add(Messages.StreamingInputCallRequest.newBuilder().setPayload(
      Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[8]))).build());
    requests.add(Messages.StreamingInputCallRequest.newBuilder().setPayload(
      Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[1828]))).build());
    requests.add(Messages.StreamingInputCallRequest.newBuilder().setPayload(
      Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[45904]))).build());

    final Messages.StreamingInputCallResponse expectedResponse =
      Messages.StreamingInputCallResponse.newBuilder().setAggregatedPayloadSize(74922).build();

    final Messages.StreamingInputCallResponse response =
      client.streamingInputCall(Source.from(requests)).toCompletableFuture().get();

    assertEquals(expectedResponse, response);
  }

  @Override
  public void clientCompressedStreaming(boolean probe) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void serverStreaming() throws Exception {
    final Messages.StreamingOutputCallRequest request =
      Messages.StreamingOutputCallRequest.newBuilder()
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(31415))
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(9))
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(2653))
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(58979))
        .build();

    final List<Messages.StreamingOutputCallResponse> expectedResponse = new ArrayList<>();
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[31415])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[9])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[2653])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[58979])))
      .build());

    final List<Messages.StreamingOutputCallResponse> response = client.streamingOutputCall(request).toMat(Sink.seq(), Keep.right()).run(mat).toCompletableFuture().get();

    assertEquals(expectedResponse.size(), response.size());
    for (int i = 0; i < expectedResponse.size(); i++) {
      assertEquals(expectedResponse.get(i), response.get(i));
    }
  }

  @Override
  public void serverCompressedStreaming() throws Exception {
    final Messages.StreamingOutputCallRequest request =
      Messages.StreamingOutputCallRequest.newBuilder()
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(31415).setCompressed(BoolValue.newBuilder().setValue(true)))
        .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(92653).setCompressed(BoolValue.newBuilder().setValue(true)))
        .build();

    final List<Messages.StreamingOutputCallResponse> expectedResponse = new ArrayList<>();
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[31415])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[92653])))
      .build());

    final List<Messages.StreamingOutputCallResponse> response = client.streamingOutputCall(request).toMat(Sink.seq(), Keep.right()).run(mat).toCompletableFuture().get();

    assertEquals(expectedResponse.size(), response.size());
    for (int i = 0; i < expectedResponse.size(); i++) {
      assertEquals(expectedResponse.get(i), response.get(i));
    }
  }

  @Override
  public void pingPong() throws Exception {
    final List<Messages.StreamingOutputCallRequest> requests = new ArrayList<>();
    requests.add(Messages.StreamingOutputCallRequest.newBuilder()
      .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(31415))
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[27182])))
      .build());
    requests.add(Messages.StreamingOutputCallRequest.newBuilder()
      .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(9))
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[8])))
      .build());
    requests.add(Messages.StreamingOutputCallRequest.newBuilder()
      .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(2653))
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[1828])))
      .build());
    requests.add(Messages.StreamingOutputCallRequest.newBuilder()
      .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(58979))
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[45904])))
      .build());

    final List<Messages.StreamingOutputCallResponse> expectedResponse = new ArrayList<>();
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[31415])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[9])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[2653])))
      .build());
    expectedResponse.add(Messages.StreamingOutputCallResponse.newBuilder()
      .setPayload(Messages.Payload.newBuilder().setBody(ByteString.copyFrom(new byte[58979])))
      .build());

    final List<Messages.StreamingOutputCallResponse> response = client.fullDuplexCall(Source.from(requests)).toMat(Sink.seq(), Keep.right()).run(mat).toCompletableFuture().get();

    assertEquals(expectedResponse.size(), response.size());
    for (int i = 0; i < expectedResponse.size(); i++) {
      assertEquals(expectedResponse.get(i), response.get(i));
    }
  }

  @Override
  public void emptyStream() throws Exception {
    final List<Messages.StreamingOutputCallResponse> response =
      client.fullDuplexCall(Source.empty()).toMat(Sink.seq(), Keep.right())
        .run(mat).toCompletableFuture().get();
    assertEquals(0, response.size());
  }

  @Override
  public void computeEngineCreds(String serviceAccount, String oauthScope) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void serviceAccountCreds(String jsonKey, InputStream credentialsStream, String authScope) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void jwtTokenCreds(InputStream serviceAccountJson) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void oauth2AuthToken(String jsonKey, InputStream credentialsStream, String authScope) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void perRpcCreds(String jsonKey, InputStream credentialsStream, String oauthScope) throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void customMetadata() throws Exception {
    // unary call
    akka.util.ByteString binaryValue = akka.util.ByteString.fromInts(0xababab);
    CompletionStage<GrpcSingleResponse<Messages.SimpleResponse>> unaryResponseCs = client.unaryCall()
        .addHeader("x-grpc-test-echo-initial", "test_initial_metadata_value")
        .addHeader("x-grpc-test-echo-trailing-bin", binaryValue)
        .invokeWithMetadata(Messages.SimpleRequest.newBuilder()
            .setResponseSize(314159)
            .setPayload(Messages.Payload.newBuilder()
                .setBody(ByteString.copyFrom(new byte[271828]))
                .build())
            .build()
        );

    GrpcSingleResponse<Messages.SimpleResponse> unaryResponse = unaryResponseCs.toCompletableFuture().get();
    Optional<String> unaryInitialMetadata = unaryResponse.getHeaders().getText("x-grpc-test-echo-initial");
    assertEquals("test_initial_metadata_value", unaryInitialMetadata.get());
    Metadata unaryTrailers = unaryResponse.getTrailers().toCompletableFuture().get();
    assertEquals(
        binaryValue,
        unaryTrailers.getBinary("x-grpc-test-echo-trailing-bin").get());

    // full duplex
    Source<Messages.StreamingOutputCallResponse, CompletionStage<GrpcResponseMetadata>> fullDuplexSource =
        client.fullDuplexCall()
          .addHeader("x-grpc-test-echo-initial", "test_initial_metadata_value")
          .addHeader("x-grpc-test-echo-trailing-bin", akka.util.ByteString.fromInts(0xababab))
          .invokeWithMetadata(Source.single(Messages.StreamingOutputCallRequest.newBuilder()
              .addResponseParameters(Messages.ResponseParameters.newBuilder().setSize(314159).build())
              .setPayload(Messages.Payload.newBuilder()
                  .setBody(ByteString.copyFrom(new byte[271828]))
                  .build())
              .build()
          ));

    Pair<CompletionStage<GrpcResponseMetadata>, CompletionStage<Messages.StreamingOutputCallResponse>> fullDuplexResult =
      fullDuplexSource.toMat(Sink.head(), Keep.both()).run(mat);

    Messages.StreamingOutputCallResponse response = fullDuplexResult.second().toCompletableFuture().get();

    GrpcResponseMetadata fullDuplexMetadata = fullDuplexResult.first().toCompletableFuture().get();
    assertEquals(
        "test_initial_metadata_value",
        fullDuplexMetadata.getHeaders().getText("x-grpc-test-echo-initial").get());

    Metadata fullDuplexTrailer = fullDuplexMetadata.getTrailers().toCompletableFuture().get();
    assertEquals(
        "Trailer should contain binary header [" + fullDuplexTrailer + "]",
        Optional.of(binaryValue),
        fullDuplexTrailer.getBinary("x-grpc-test-echo-trailing-bin"));
  }

  @Override
  public void statusCodeAndMessage() throws Exception {
    // assert unary
    final String errorMessage = "test status message";
    final Messages.EchoStatus echoStatus = Messages.EchoStatus.newBuilder()
      .setCode(Status.UNKNOWN.getCode().value())
      .setMessage(errorMessage)
      .build();
    final Messages.SimpleRequest request = Messages.SimpleRequest.newBuilder()
      .setResponseStatus(echoStatus)
      .build();

    final CompletionStage<Messages.SimpleResponse> response = client.unaryCall(request);
    response.toCompletableFuture().handle((res, ex) -> {
      if (!(ex instanceof StatusRuntimeException))
        fail("Expected [StatusRuntimeException] but got " + (ex == null ? "null" : ex.getClass().toString()));

      final StatusRuntimeException e = (StatusRuntimeException)ex;
      assertEquals(Status.UNKNOWN.getCode(), e.getStatus().getCode());
      assertEquals(errorMessage, e.getStatus().getDescription());

      return null;
    }).get();

    // assert streaming
    final Messages.StreamingOutputCallRequest streamingRequest =
      Messages.StreamingOutputCallRequest.newBuilder().setResponseStatus(echoStatus).build();
    final CompletionStage<Messages.StreamingOutputCallResponse> streamingResponse =
      client.fullDuplexCall(Source.single(streamingRequest)).runWith(Sink.head(), mat);
    streamingResponse.toCompletableFuture().handle((res, ex) -> {
      if (!(ex instanceof StatusRuntimeException))
        fail("Expected [StatusRuntimeException] but got " + (ex == null ? "null" : ex.getClass().toString()));

      final StatusRuntimeException e = (StatusRuntimeException)ex;
      assertEquals(Status.UNKNOWN.getCode(), e.getStatus().getCode());
      assertEquals(errorMessage, e.getStatus().getDescription());

      return null;
    }).get();
  }

  @Override
  public void unimplementedMethod() {
    try {
      client.unimplementedCall(EmptyProtos.Empty.newBuilder().build()).toCompletableFuture()
        .handle((res, ex) -> {
          if (!(ex instanceof StatusRuntimeException))
            fail("Expected [StatusRuntimeException] but got " + (ex == null ? "null" : ex.getClass().toString()));

          final StatusRuntimeException e = (StatusRuntimeException) ex;
          assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());

          return null;
        }).get();
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void unimplementedService() {
    try {
      clientUnimplementedService.unimplementedCall(EmptyProtos.Empty.newBuilder().build()).toCompletableFuture()
        .handle((res, ex) -> {
          if (!(ex instanceof StatusRuntimeException))
            fail("Expected [StatusRuntimeException] but got " + (ex == null ? "null" : ex.getClass().toString()));

          final StatusRuntimeException e = (StatusRuntimeException) ex;
          assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());

          return null;
        }).get();
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void cancelAfterBegin() throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void cancelAfterFirstResponse() throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public void timeoutOnSleepingServer() throws Exception {
    throw new UnsupportedOperationException("Not implemented!");
  }
}
