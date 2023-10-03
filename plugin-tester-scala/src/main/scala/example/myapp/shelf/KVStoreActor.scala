package example.myapp.shelf

import akka.actor.typed.scaladsl._
import akka.actor.typed._

object KVStoreActor {
  sealed trait KVStoreCommand[K, V]
  final case class Get[K, V](key: K, reply: ActorRef[Option[V]]) extends KVStoreCommand[K, V]
  final case class Create[K, V](key: K, value: V, reply: ActorRef[Option[V]]) extends KVStoreCommand[K, V]
  final case class Delete[K, V](key: K, reply: ActorRef[Option[V]]) extends KVStoreCommand[K, V]

  def apply[K, V](): Behavior[KVStoreCommand[K, V]] = store(Map.empty[K, V])
  private def store[K, V](storage: Map[K, V]): Behavior[KVStoreCommand[K, V]] = {
    Behaviors.receiveMessage[KVStoreCommand[K, V]] {
      case Get(key, reply) => {
        reply ! storage.get(key)
        Behaviors.same
      }

      case Create(key, value, reply) => {
        val previous = storage.get(key)
        if (previous.isEmpty) {
          reply ! Some(value)
          store(storage.updated(key, value))
        } else {
          reply ! None
          Behaviors.same
        }
      }

      case Delete(key, reply) => {
        val previous = storage.get(key)
        previous.fold {
          reply ! None
          Behaviors.same[KVStoreCommand[K, V]]
        }(value => {
          reply ! Some(value)
          store(storage - key)
        })
      }
    }
  }
}
