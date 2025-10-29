/*
 * Copyright (C) 2024-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestServiceImpl implements TestService {
    @Override
    public CompletionStage<PretendProtoMessage> echo(PretendProtoMessage in) {
        return CompletableFuture.completedFuture(in);
    }
}
