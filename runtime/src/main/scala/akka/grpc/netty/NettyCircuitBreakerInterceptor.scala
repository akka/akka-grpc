package akka.grpc.netty

import akka.pattern.CircuitBreaker
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

/**
 * A `io.grpc.ClientInterceptor` that prevents downstream overload wrapping the calls with a circuit breaker.
 */
class NettyCircuitBreakerInterceptor(breaker: CircuitBreaker) extends ClientInterceptor {

  private class Listener[RespT](delegate: ClientCall.Listener[RespT])
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener[RespT](delegate) {
    override def onClose(status: Status, trailers: Metadata): Unit = {
      if (status.isOk) {
        breaker.succeed()
      } else {
        breaker.fail()
      }

      super.onClose(status, trailers)
    }
  }

  override def interceptCall[ReqT, RespT](
      method: MethodDescriptor[ReqT, RespT],
      callOptions: CallOptions,
      next: Channel): ClientCall[ReqT, RespT] =
    new ClientInterceptors.CheckedForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {

      override def checkedStart(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
        delegate().start(new Listener(responseListener), headers)
      }

      override def isReady: Boolean = {
        if (breaker.isClosed || breaker.isHalfOpen) {
          super.isReady
        } else {
          throw new IllegalStateException("Circuit Breaker is open. Can't send messages.")
        }
      }

      override def sendMessage(message: ReqT): Unit = {
        if (breaker.isClosed || breaker.isHalfOpen) {
          delegate().sendMessage(message)
        }
      }
    }

}
