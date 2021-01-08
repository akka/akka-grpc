package akka.grpc.internal

import akka.actor.{
  ActorSystem,
  ClassicActorSystemProvider,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider
}
import akka.annotation.{ InternalApi, InternalStableApi }
import akka.http.javadsl.model.HttpRequest

import scala.annotation.nowarn

class TelemetryExtensionImpl(val spi: TelemetrySpi) extends Extension

object TelemetryExtension extends ExtensionId[TelemetryExtensionImpl] with ExtensionIdProvider {
  override def lookup = TelemetryExtension
  override def createExtension(system: ExtendedActorSystem) =
    new TelemetryExtensionImpl(TelemetrySpi(system))

  /** Java API */
  override def get(system: ActorSystem): TelemetryExtensionImpl = super.get(system)
  override def get(system: ClassicActorSystemProvider): TelemetryExtensionImpl = super.get(system)
}

private[internal] object TelemetrySpi {
  private val ConfigKey = "akka.grpc.telemetry-class"
  def apply(system: ClassicActorSystemProvider): TelemetrySpi = {
    if (!system.classicSystem.settings.config.hasPath(ConfigKey)) NoOpTelemetry
    else {
      val fqcn = system.classicSystem.settings.config.getString(ConfigKey)
      try {
        system.classicSystem
          .asInstanceOf[ExtendedActorSystem]
          .dynamicAccess
          .createInstanceFor[TelemetrySpi](fqcn, Nil)
          .get
      } catch {
        case ex: Throwable =>
          system.classicSystem.log.debug(
            "{} references a class that could not be instantiated ({}) falling back to no-op implementation",
            fqcn,
            ex.toString)
          NoOpTelemetry
      }
    }
  }
}

@InternalStableApi
trait TelemetrySpi {
  @nowarn
  def onRequest[T <: HttpRequest](prefix: String, method: String, request: T): T = request
}

@InternalApi
private[internal] object NoOpTelemetry extends TelemetrySpi
