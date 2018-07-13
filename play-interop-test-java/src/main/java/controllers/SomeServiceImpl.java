/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package controllers;

import example.myapp.someservice.grpc.SomeReply;
import example.myapp.someservice.grpc.SomeRequest;
import example.myapp.someservice.grpc.SomeService;

import java.util.concurrent.CompletionStage;

// just a dummy for now, needed until #291 is merged
public class SomeServiceImpl implements SomeService {
  @Override
  public CompletionStage<SomeReply> doSomething(SomeRequest in) {
    return null;
  }
}
