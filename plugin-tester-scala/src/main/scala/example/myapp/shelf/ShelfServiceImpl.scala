package example.myapp.shelf

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import example.myapp.shelf.KVStoreActor.KVStoreCommand
import example.myapp.shelf.grpc._
import io.grpc.Status

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class ShelfServiceImpl(storage: ActorRef[KVStoreCommand[Long, Shelf]])(implicit system: ActorSystem[_])
    extends ShelfService {

  implicit val timeout: Timeout = Timeout(10.seconds)
  implicit val ec: ExecutionContext = system.executionContext

  import KVStoreActor._

  /**
   * Returns a specific bookstore shelf.
   */
  def getShelf(in: GetShelfRequest): Future[Shelf] = {
    storage.ask[Option[Shelf]](replyTo => Get(in.shelf, replyTo)).flatMap {
      case Some(value) => Future.successful(value)
      case None =>
        Future.failed(
          new GrpcServiceException(
            Status.NOT_FOUND.withDescription("Didn't find requested shelf, please make sure it has been created.")))
    }
  }

  def createShelf(in: CreateShelfRequest): Future[Shelf] = {
    in.shelf match {
      case Some(shelfData) =>
        storage.ask[Option[Shelf]](replyTo => Create(shelfData.id, shelfData, replyTo)).flatMap {
          case Some(value) => Future.successful(value)
          case None        => Future.failed(new GrpcServiceException(Status.ALREADY_EXISTS))
        }
      case None => Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT))
    }
  }

  def deleteShelf(in: DeleteShelfRequest): Future[Shelf] = {
    storage.ask[Option[Shelf]](replyTo => Delete(in.shelf, replyTo)).flatMap {
      case Some(value) => Future.successful(value)
      case None        => Future.failed(new GrpcServiceException(Status.NOT_FOUND))
    }
  }

  def updateShelf(in: UpdateShelfRequest): Future[Shelf] = {
    Future.successful(in.shelf.get)
  }
}
