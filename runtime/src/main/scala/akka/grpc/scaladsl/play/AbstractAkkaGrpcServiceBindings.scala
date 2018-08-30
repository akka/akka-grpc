package akka.grpc.scaladsl.play

import play.api.inject.Binding
import play.api.{ Configuration, Environment, Logger }

import scala.annotation.varargs
import scala.reflect.ClassTag

/**
 * Helper for generating Play service bindings.
 */
abstract class AbstractAkkaGrpcServiceModule extends play.api.inject.Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    services.flatMap(bindingsForService(_, environment, configuration))

  protected def services: Seq[Class[_]]

  @varargs // Helper for Java subclasses
  final protected def classSeq(classes: Class[_]*): Seq[Class[_]] = classes

  protected def bindingsForService[T](serviceClass: Class[T], environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val logger = Logger(classOf[AbstractAkkaGrpcServiceModule])

    val baseConfigPath = s"""akka.grpc.service."${serviceClass.getName}""""
    val enabledConfig = s"$baseConfigPath.enabled"
    val classNameConfig = s"$baseConfigPath.class"

    if (!configuration.has(enabledConfig) || configuration.get[Boolean](enabledConfig)) {
      // We support disabling service loading, since some generated services might not actually be needed

      logger.info(s"Service ${serviceClass.getName} not bound to an implementation clas because setting '$enabledConfig' is false")
      Seq.empty
    } else {
      // Service loading is enabled

      // Get the service implementation class name and a bit of diagnostic info about how it was loaded
      val (implClassName, logReason): (String, String) = if (configuration.has(classNameConfig)) {
        (configuration.get[String](classNameConfig), s"implementation class name read from configuration at $classNameConfig")
      } else {
        (serviceClass.getName + "Impl", s"no configuration value at $classNameConfig, so using default implementation class name")
      }
      logger.debug(s"Binding service interface ${serviceClass.getName} to implementation class ${implClassName}: $logReason")

      val implClass: Class[_ <: T] = try Class.forName(implClassName, false, environment.classLoader).asInstanceOf[Class[_ <: T]] catch {
        case origException: ClassNotFoundException =>
          throw new ClassNotFoundException(
            s"Failed to load implementation class $implClassName needed to bind service ${serviceClass.getName}. " +
              s"To disable binding for this service, set config value '$enabledConfig' to false. To override the implementation " +
              s"class name, set config value '$classNameConfig' to the full class name to use instead: $logReason",
            origException)
      }

      if (!serviceClass.isAssignableFrom(implClass)) {
        throw new ClassCastException(s"Implementation class $implClassName does not implement service ${serviceClass.getName}")
      }

      Seq(bind(serviceClass).qualifiedWith("impl").to(implClass))
    }
  }

}
