package akka.grpc.internal

import java.util.concurrent.CompletionStage

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
  def close(): Future[Done]
  def closed(): Future[Done]
}

@ApiMayChange
trait JavaAkkaGrpcClient {
  def close(): CompletionStage[Done]
  def closed(): CompletionStage[Done]
}
