package akka.grpc.scaladsl

import scala.concurrent.Future

import akka.Done
import akka.annotation.DoNotInherit

/**
 * Common trait of all generated Akka gRPC clients. Not for user extension.
 */
@DoNotInherit
trait AkkaGrpcClient {
  def close(): Future[Done]
  def closed(): Future[Done]
}
