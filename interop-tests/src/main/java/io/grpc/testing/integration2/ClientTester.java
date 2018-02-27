package io.grpc.testing.integration2;

import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.google.protobuf.EmptyProtos;
import io.grpc.*;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.testing.StatsTestUtils;
import io.grpc.internal.testing.StreamRecorder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.AbstractInteropTest;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.TestCases;
import io.grpc.testing.integration.TestServiceGrpc;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.testing.integration.Messages.PayloadType.COMPRESSABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public interface ClientTester {

    ManagedChannel createChannel();

    void setUp();

    void tearDown() throws Exception;

    void emptyUnary() throws Exception;

    void cacheableUnary();

    void largeUnary() throws Exception;

    void clientCompressedUnary() throws Exception;

    void serverCompressedUnary() throws Exception;

    void clientStreaming() throws Exception;

    void clientCompressedStreaming() throws Exception;

    void serverStreaming() throws Exception;

    void serverCompressedStreaming() throws Exception;
    
    void pingPong() throws Exception;

    void emptyStream() throws Exception;

    void computeEngineCreds(String serviceAccount, String oauthScope) throws Exception;

    void serviceAccountCreds(String jsonKey, InputStream credentialsStream, String authScope) throws Exception;

    void jwtTokenCreds(InputStream serviceAccountJson) throws Exception;

    void oauth2AuthToken(String jsonKey, InputStream credentialsStream, String authScope) throws Exception;

    void perRpcCreds(String jsonKey, InputStream credentialsStream, String oauthScope) throws Exception;

    void customMetadata() throws Exception;

    void statusCodeAndMessage() throws Exception;

    void unimplementedMethod();

    void unimplementedService();

    void cancelAfterBegin() throws Exception;

    void cancelAfterFirstResponse() throws Exception;

    void timeoutOnSleepingServer() throws Exception;


}
