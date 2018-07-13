/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.gen

import java.io.File

import example.myapp.helloworld.grpc.{ GreeterServiceClient, GreeterServiceClientProvider }
import example.myapp.someservice.grpc.{ SomeServiceClient, SomeServiceClientProvider }
import org.scalatest.{ Matchers, WordSpec }
import play.api.inject.ProviderConstructionTarget
import play.api.{ Configuration, Environment, Mode }

class PlayJavaModuleSpec extends WordSpec with Matchers {

  "The generated module" should {

    "provide all clients" in {
      // module in longest common package for the two services
      val module = new example.myapp.AkkaGrpcClientModule()

      val bindings = module.bindings(Environment(new File("./"), getClass.getClassLoader, Mode.Prod), Configuration.empty)

      // both clients should be in there
      bindings should have size (2)

      bindings.map(_.key.clazz).toSet should ===(Set(classOf[GreeterServiceClient], classOf[SomeServiceClient]))

      // not super useful assertions but let's keep for good measure
      bindings.map(_.target.get.asInstanceOf[ProviderConstructionTarget[_]].provider).toSet should ===(Set(classOf[GreeterServiceClientProvider], classOf[SomeServiceClientProvider]))
    }

  }

}
