/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package example.myapp.echo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import example.myapp.echo.grpc.*;

public class EchoServiceImpl implements EchoService {

  @Override
  public CompletionStage<EchoMessage> echo(EchoMessage in) {
    return CompletableFuture.completedFuture(in);
  }
}

