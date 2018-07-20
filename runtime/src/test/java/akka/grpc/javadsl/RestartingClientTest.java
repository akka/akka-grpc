/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl;

import akka.Done;
import akka.actor.ActorSystem;
import akka.grpc.JUnitEventually;
import akka.grpc.internal.ClientConnectionException;
import akka.grpc.internal.JavaAkkaGrpcClient;
import akka.grpc.scaladsl.RestartingClientSpec;
import org.junit.Test;
import org.scalactic.source.Position;
import org.scalatest.concurrent.Eventually$;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import scala.compat.java8.FutureConverters$;

import static org.junit.Assert.*;

public class RestartingClientTest extends JUnitEventually {

    public static class FakeJavaClient implements JavaAkkaGrpcClient {

        private RestartingClientSpec.FakeClient delegate = new RestartingClientSpec.FakeClient();

        @Override
        public CompletionStage<Done> close() {
            return FutureConverters$.MODULE$.toJava(delegate.close());
        }

        @Override
        public CompletionStage<Done> closed() {
            return FutureConverters$.MODULE$.toJava(delegate.closed());
        }

        @Override
        public String toString() {
            return "FakeJavaClient{" +
                    "delegate=" + delegate +
                    '}';
        }
    }


    @Test
    public void shouldWork() throws Exception {
        ArrayBlockingQueue<FakeJavaClient> clientCreations = new ArrayBlockingQueue<FakeJavaClient>(10);
        RestartingClient<FakeJavaClient> restartingClient = new RestartingClient<>(
                () -> {
                    FakeJavaClient c = new FakeJavaClient();
                    clientCreations.offer(c);
                    return c;
                }, Executors.newCachedThreadPool()
        );

        FakeJavaClient firstClient = clientCreations.poll(10, TimeUnit.MILLISECONDS);
        assertNotNull(firstClient);
        assertEquals(0, clientCreations.size());

        firstClient.delegate.fail(new ClientConnectionException("Oh noes"));

        FakeJavaClient secondClient = clientCreations.poll(10, TimeUnit.MILLISECONDS);
        assertNotNull(secondClient);
        assertEquals(0, clientCreations.size());

        junitEventually(() -> {
            assertEquals("best", restartingClient.withClient(c -> {
                assertEquals(secondClient, c);
                return c.delegate.bestClientCallEver();
            }));
            return "be happy java compile";
        });

        assertFalse(secondClient.delegate.beenClosed());

        restartingClient.close().toCompletableFuture().get();

        junitEventually(() -> {
            assertTrue("Second client: " + secondClient, secondClient.delegate.beenClosed());
            return "Keep compiler happy with Scala Unit";
        });
    }
}
