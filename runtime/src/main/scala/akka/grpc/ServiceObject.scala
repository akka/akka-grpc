package akka.grpc

import com.google.protobuf.Descriptors.FileDescriptor;

trait ServiceObject {
  def name: String
  def descriptor: FileDescriptor
}
