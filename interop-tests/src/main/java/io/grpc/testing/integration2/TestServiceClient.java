/*
 * Copyright 2014, gRPC Authors All rights reserved.
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

package io.grpc.testing.integration2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import io.grpc.internal.testing.TestUtils;
import io.grpc.testing.integration.TestCases;
import io.grpc.testing.integration.TestServiceGrpc;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;

/**
 * Application that starts a client for the {@link TestServiceGrpc.TestServiceImplBase} and runs
 * through a series of tests.
 */
public class TestServiceClient {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private String testCase = "empty_unary";

    /**
     * The main application allowing this client to be launched from the command line.
     */
    public static void main(String[] args) throws Exception {
        // Let OkHttp use Conscrypt if it is available.
        TestUtils.installConscryptIfAvailable();
        Settings settings = Settings.parseArgs(args);
        final TestServiceClient client = new TestServiceClient(new GrpcJavaClientTester(settings));
        client.setUp();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down");
                try {
                    client.tearDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            client.run(settings);
        } finally {
            client.tearDown();
        }
        System.exit(0);
    }

    private ClientTester clientTester;

    public TestServiceClient(ClientTester clientTester) {
        this.clientTester = clientTester;
    }

    @VisibleForTesting
    public void setUp() {
        clientTester.setUp();
    }

    public synchronized void tearDown() {
        try {
            clientTester.tearDown();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void run(Settings settings) {
        System.out.println("Running test " + settings.getTestCase());
        try {
            runTest(TestCases.fromString(settings.getTestCase()), settings);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        System.out.println("Test completed.");
    }

    private void runTest(TestCases testCase, Settings settings) throws Exception {
        switch (testCase) {
            case EMPTY_UNARY:
                clientTester.emptyUnary();
                break;

            case LARGE_UNARY:
                clientTester.largeUnary();
                break;

            case CLIENT_COMPRESSED_UNARY:
                clientTester.clientCompressedUnary(false);
                break;

            case SERVER_COMPRESSED_UNARY:
                clientTester.serverCompressedUnary();
                break;

            case CLIENT_STREAMING:
                clientTester.clientStreaming();
                break;

            case CLIENT_COMPRESSED_STREAMING:
                clientTester.clientCompressedStreaming(false);
                break;

            case SERVER_STREAMING:
                clientTester.serverStreaming();
                break;

            case SERVER_COMPRESSED_STREAMING:
                clientTester.serverCompressedStreaming();
                break;

            case PING_PONG:
                clientTester.pingPong();
                break;

            case EMPTY_STREAM:
                clientTester.emptyStream();
                break;

            case CUSTOM_METADATA: {
                clientTester.customMetadata();
                break;
            }

            case STATUS_CODE_AND_MESSAGE: {
                clientTester.statusCodeAndMessage();
                break;
            }

            case UNIMPLEMENTED_METHOD: {
                clientTester.unimplementedMethod();
                break;
            }

            case UNIMPLEMENTED_SERVICE: {
                clientTester.unimplementedService();
                break;
            }

            case CANCEL_AFTER_BEGIN: {
                clientTester.cancelAfterBegin();
                break;
            }

            case CANCEL_AFTER_FIRST_RESPONSE: {
                clientTester.cancelAfterFirstResponse();
                break;
            }

            case TIMEOUT_ON_SLEEPING_SERVER: {
                clientTester.timeoutOnSleepingServer();
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown test case: " + testCase);
        }
    }


}
