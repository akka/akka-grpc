/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.google.protobuf.Descriptors.FileDescriptor

import akka.NotUsed
import akka.annotation.InternalApi
import akka.stream.scaladsl._

import _root_.grpc.reflection.v1alpha.reflection._

/**
 * INTERNAL API
 */
@InternalApi
final class ServerReflectionImpl private (fileDescriptors: Map[String, FileDescriptor], services: List[String])
    extends ServerReflection {
  import ServerReflectionImpl._

  def serverReflectionInfo(in: Source[ServerReflectionRequest, NotUsed]): Source[ServerReflectionResponse, NotUsed] = {
    in.map(req => {
      import ServerReflectionRequest.{ MessageRequest => In }
      import ServerReflectionResponse.{ MessageResponse => Out }

      val response = req.messageRequest match {
        case In.Empty =>
          Out.Empty
        case In.FileByFilename(fileName) =>
          val list = fileDescriptors.get(fileName).map(_.toProto.toByteString).toList
          Out.FileDescriptorResponse(FileDescriptorResponse(list))
        case In.FileContainingSymbol(symbol) =>
          val list = findFileDescForSymbol(symbol, fileDescriptors).map(_.toProto.toByteString).toList
          Out.FileDescriptorResponse(FileDescriptorResponse(list))
        case In.FileContainingExtension(ExtensionRequest(container, number)) =>
          val list = findFileDescForExtension(container, number, fileDescriptors).map(_.toProto.toByteString).toList
          Out.FileDescriptorResponse(FileDescriptorResponse(list))
        case In.AllExtensionNumbersOfType(container) =>
          val list = findExtensionNumbersForContainingType(container, fileDescriptors) // TODO should we throw a NOT_FOUND if we don't know the container type at all?
          Out.AllExtensionNumbersResponse(ExtensionNumberResponse(container, list))
        case In.ListServices(_) =>
          val list = services.map(s => ServiceResponse(s))
          Out.ListServicesResponse(ListServiceResponse(list))
      }
      // TODO Validate assumptions here
      ServerReflectionResponse(req.host, Some(req), response)
    })
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object ServerReflectionImpl {
  import scala.collection.JavaConverters._

  def apply(fileDescriptors: Seq[FileDescriptor], services: List[String]): ServerReflectionImpl =
    new ServerReflectionImpl(
      (AdditionalDescriptors ++ fileDescriptors).map(fd => fd.getName -> fd).toMap,
      ServerReflection.name +: services)

  val AdditionalDescriptors =
    flattenDependencies(ReflectionProto.javaDescriptor).distinct

  private def flattenDependencies(descriptor: FileDescriptor): List[FileDescriptor] = {
    descriptor :: descriptor.getDependencies.asScala.toList.flatMap(flattenDependencies)
  }

  def splitNext(name: String): (String, String) = {
    val dot = name.indexOf('.')
    if (dot >= 0) {
      (name.substring(0, dot), name.substring(dot + 1))
    } else {
      (name, "")
    }
  }

  def containsSymbol(symbol: String, fileDesc: FileDescriptor): Boolean =
    (symbol.startsWith(fileDesc.getPackage)) && // Ensure package match first
    (splitNext(if (fileDesc.getPackage.isEmpty) symbol else symbol.drop(fileDesc.getPackage.length + 1)) match {
      case ("", "")            => false
      case (typeOrService, "") =>
        //fileDesc.findEnumTypeByName(typeOrService) != null || // TODO investigate if this is expected
        fileDesc.findMessageTypeByName(typeOrService) != null ||
        fileDesc.findServiceByName(typeOrService) != null
      case (service, method) =>
        Option(fileDesc.findServiceByName(service)).exists(_.findMethodByName(method) != null)
    })

  def findFileDescForSymbol(symbol: String, fileDescriptors: Map[String, FileDescriptor]): Option[FileDescriptor] =
    fileDescriptors.values.collectFirst {
      case fileDesc if containsSymbol(symbol, fileDesc) => fileDesc
    }

  def containsExtension(container: String, number: Int, fileDesc: FileDescriptor): Boolean =
    fileDesc.getExtensions.iterator.asScala.exists(ext =>
      container == ext.getContainingType.getFullName && number == ext.getNumber)

  def findFileDescForExtension(
      container: String,
      number: Int,
      fileDescriptors: Map[String, FileDescriptor]): Option[FileDescriptor] =
    fileDescriptors.values.collectFirst {
      case fileDesc if containsExtension(container, number, fileDesc) => fileDesc
    }

  def findExtensionNumbersForContainingType(
      container: String,
      fileDescriptors: Map[String, FileDescriptor]): List[Int] =
    (for {
      fileDesc <- fileDescriptors.values.iterator
      extension <- fileDesc.getExtensions.iterator.asScala
      if extension.getFullName == container
    } yield extension.getNumber).toList
}
