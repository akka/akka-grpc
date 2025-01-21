/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal;

import akka.grpc.AkkaGrpcGenerated;

import java.util.concurrent.CompletionStage;

// this would be generated
@AkkaGrpcGenerated
public interface TestService {
    class PretendProtoMessage {
        final String text;
        public PretendProtoMessage(String text) {
            this.text = text;
        }
    }

    CompletionStage<PretendProtoMessage> echo(PretendProtoMessage in);
}
