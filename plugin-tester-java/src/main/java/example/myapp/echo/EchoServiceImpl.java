/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.echo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import example.myapp.echo.grpc.*;

public class EchoServiceImpl implements EchoService {

  @Override
  public CompletionStage<EchoMessage> echo(EchoMessage in) {
    return CompletableFuture.completedFuture(in);
  }
}

