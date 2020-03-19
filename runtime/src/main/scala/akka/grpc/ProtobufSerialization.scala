package akka.grpc

/**
 * A scheme for serialization of protobuf objects into protocol frames.
 */
sealed trait ProtobufSerialization {

  /** The name of this serialization, suitable for inclusion in a media type */
  def name: String
}

object ProtobufSerialization {

  /**
   * A set of items selected by a serialization format.
   * @param proto
   * @param json
   * @tparam T
   */
  class Selector[T] private[ProtobufSerialization] (proto: T, json: T) {

    implicit def apply(implicit format: ProtobufSerialization): T = format match {
      case Protobuf => proto
      case Json     => json
    }

    /* Java API */
    /**
     * Selects an item for the given serialization format.
     */
    def select(format: ProtobufSerialization): T = apply(format)
  }

  /**
   * The standard protobuf binary serialization.
   */
  case object Protobuf extends ProtobufSerialization {
    override val name: String = "proto"
  }

  /**
   * Protobuf JSON serialization.
   */
  case object Json extends ProtobufSerialization {
    override val name: String = "json"
  }

  def selector[T](proto: T, json: T): Selector[T] = new Selector[T](proto, json)

  /* Java API */
  val protobuf: ProtobufSerialization = Protobuf
  val json: ProtobufSerialization = Json

}
