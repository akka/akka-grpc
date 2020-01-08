package akka.grpc

import com.google.protobuf.Descriptors.FileDescriptor;

class ServiceObjectImpl(val name: String, val descriptor: FileDescriptor) extends ServiceObject
