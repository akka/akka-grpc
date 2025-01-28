/*
 * Copyright (C) 2018-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package io.grpc.testing.integration2;

import io.grpc.ManagedChannel;

import java.io.InputStream;

/**
 *  This class has all the methods of the grpc-java AbstractInteropTest, but none of the implementations,
 *  so it can implement either by calling AbstractInteropTest or with an Akka gRPC implementation.
 *  https://github.com/grpc/grpc-java/blob/master/interop-testing/src/main/java/io/grpc/testing/integration/AbstractInteropTest.java
 *
 * Test requirements documentation: https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md
 */
public interface ClientTester {

    void setUp();

    void tearDown() throws Exception;

    void emptyUnary() throws Exception;

    void largeUnary() throws Exception;

    void clientCompressedUnary(boolean probe) throws Exception;

    void serverCompressedUnary() throws Exception;

    void clientStreaming() throws Exception;

    void clientCompressedStreaming(boolean probe) throws Exception;

    void serverStreaming() throws Exception;

    void serverCompressedStreaming() throws Exception;
    
    void pingPong() throws Exception;

    void emptyStream() throws Exception;

    void customMetadata() throws Exception;

    void statusCodeAndMessage() throws Exception;

    void unimplementedMethod();

    void unimplementedService();

    void cancelAfterBegin() throws Exception;

    void cancelAfterFirstResponse() throws Exception;

    void timeoutOnSleepingServer() throws Exception;


}
