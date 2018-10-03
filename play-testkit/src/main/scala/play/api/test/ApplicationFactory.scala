/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

import play.api._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.routing.Router

// RICH: This is taken from the Play integration tests; it should replace that old
// code when merged back into Play.

/**
 * Creates an [[Application]]. Usually created by a helper in [[ApplicationFactories]].
 */
trait ApplicationFactory {
  /** Creates an [[Application]]. */
  def create(): Application
}

/**
 * Mixin with helpers for creating [[ApplicationFactory]] objects.
 */
// RICH: I recommend that this is merged into PlaySpec so that users can easily create applications
trait ApplicationFactories {
  def appFromGuice(builder: GuiceApplicationBuilder): ApplicationFactory = new ApplicationFactory {
    override def create(): Application = builder.build()
  }
  def appFromComponents(components: => BuiltInComponents): ApplicationFactory = new ApplicationFactory {
    override def create(): Application = components.application
  }
  def appFromRouter(createRouter: BuiltInComponents => Router): ApplicationFactory =
    appFromConfigAndRouter(Map.empty)(createRouter)
  def appFromConfigAndRouter(extraConfig: Map[String, Any])(createRouter: BuiltInComponents => Router): ApplicationFactory = appFromComponents {
    val context = ApplicationLoader.Context.create(
      environment = Environment.simple(),
      initialSettings = Map[String, AnyRef](Play.GlobalAppConfigKey -> java.lang.Boolean.FALSE) ++ extraConfig.asInstanceOf[Map[String, AnyRef]])
    new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {
      override lazy val router: Router = createRouter(this)
    }
  }
  def appFromAction(createAction: DefaultActionBuilder => Action[_]): ApplicationFactory = appFromRouter { components: BuiltInComponents =>
    val action = createAction(components.defaultActionBuilder)
    Router.from { case _ => action }
  }
  def appFromResult(result: Result): ApplicationFactory = appFromAction { Action: DefaultActionBuilder =>
    Action { result }
  }
}

final object ApplicationFactory extends ApplicationFactories
