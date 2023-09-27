package example.myapp.shelf

import example.myapp.shelf.grpc.{ GetShelfRequest, Shelf, ShelfService }

import scala.concurrent.Future

class ShelfServiceImpl extends ShelfService {

  /**
   * Returns a specific bookstore shelf.
   */
  def getShelf(in: GetShelfRequest): Future[Shelf] = Future.successful(Shelf(1, "halloween"))
}
