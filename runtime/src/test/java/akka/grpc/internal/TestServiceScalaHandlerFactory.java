/*
 * Copyright (C) 2024-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal;

import akka.actor.ClassicActorSystemProvider;
import akka.grpc.Trailers;
import akka.grpc.scaladsl.InstancePerRequestFactory;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import scala.Function1;
import scala.PartialFunction;
import scala.concurrent.Future;

// this would be generated
public class TestServiceScalaHandlerFactory implements InstancePerRequestFactory<TestService> {


    @Override
    public PartialFunction<HttpRequest, Future<HttpResponse>> partialInstancePerRequest(Function1<HttpRequest, TestService> serviceFactory, String prefix, PartialFunction<Throwable, Trailers> eHandler, ClassicActorSystemProvider systemProvider, Materializer materializer) {
        return null;
    }

    @Override
    public PartialFunction<HttpRequest, Future<HttpResponse>> partialInstancePerRequest(Function1<HttpRequest, TestService> serviceFactory, String prefix, PartialFunction<Throwable, Trailers> eHandler, ClassicActorSystemProvider systemProvider) {
        return null;
    }
}
