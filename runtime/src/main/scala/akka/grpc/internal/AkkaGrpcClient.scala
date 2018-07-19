package akka.grpc.internal

import akka.Done
import akka.annotation.ApiMayChange

import scala.concurrent.Future

/**
 * INTERNAL API
 *
 * Public as is included in generated code.
 */
@ApiMayChange
trait AkkaGrpcClient {
  def closed(): Future[Done]
  def close(): Future[Done]
}
