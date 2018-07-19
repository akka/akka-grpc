package akka.grpc.javadsl;

import akka.Done;
import akka.actor.ActorSystem;
import akka.grpc.internal.ClientConnectionException;
import akka.grpc.internal.JavaAkkaGrpcClient;
import akka.grpc.scaladsl.RestartingClientSpec;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import scala.compat.java8.FutureConverters$;

import static org.junit.Assert.*;

public class RestartingClientTest extends JUnitSuite {

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

        firstClient.delegate.fail(new ClientConnectionException("Oh nodes"));

        FakeJavaClient secondClient = clientCreations.poll(10, TimeUnit.MILLISECONDS);
        assertNotNull(secondClient);
        assertEquals(0, clientCreations.size());

        assertEquals("best", restartingClient.withClient(c -> c.delegate.bestClientCallEver()));

        assertFalse(secondClient.delegate.beenClosed());

        restartingClient.close().toCompletableFuture().get();

        assertTrue(secondClient.delegate.beenClosed());
    }
}
