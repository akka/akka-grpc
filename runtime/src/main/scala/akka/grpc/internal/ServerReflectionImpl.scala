/*
 * Copyright (C) 2019-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import com.google.protobuf.Descriptors.FileDescriptor
import akka.NotUsed
import akka.annotation.InternalApi
import akka.stream.scaladsl._
import _root_.grpc.reflection.v1alpha.reflection._
import com.google.protobuf.ByteString

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent
import scala.jdk.CollectionConverters._

/**
 * INTERNAL API
 */
@InternalApi
final class ServerReflectionImpl private (fileDescriptors: Map[String, FileDescriptor], services: List[String])
    extends ServerReflection {
  import ServerReflectionImpl._
  import ServerReflectionResponse.{ MessageResponse => Out }

  private val protoBytesLocalCache: concurrent.Map[String, ByteString] =
    new ConcurrentHashMap[String, ByteString]().asScala

  def serverReflectionInfo(in: Source[ServerReflectionRequest, NotUsed]): Source[ServerReflectionResponse, NotUsed] = {
    // The server reflection spec requires sending transitive dependencies, but allows (and encourages) to only send
    // transitive dependencies that haven't yet been sent on this stream. So, we track this with a stateful map.
    in.statefulMap(() => Set.empty[String])(
      (alreadySent, req) => {

        import ServerReflectionRequest.{ MessageRequest => In }

        val (newAlreadySent, response) = req.messageRequest match {
          case In.Empty =>
            (alreadySent, Out.Empty)
          case In.FileByFilename(fileName) =>
            toFileDescriptorResponse(fileDescriptors.get(fileName), alreadySent)
          case In.FileContainingSymbol(symbol) =>
            toFileDescriptorResponse(findFileDescForSymbol(symbol, fileDescriptors), alreadySent)
          case In.FileContainingExtension(ExtensionRequest(container, number, _)) =>
            toFileDescriptorResponse(findFileDescForExtension(container, number, fileDescriptors), alreadySent)
          case In.AllExtensionNumbersOfType(container) =>
            val list =
              findExtensionNumbersForContainingType(
                container,
                fileDescriptors
              ) // TODO should we throw a NOT_FOUND if we don't know the container type at all?
            (alreadySent, Out.AllExtensionNumbersResponse(ExtensionNumberResponse(container, list)))
          case In.ListServices(_) =>
            val list = services.map(s => ServiceResponse(s))
            (alreadySent, Out.ListServicesResponse(ListServiceResponse(list)))
        }
        // TODO Validate assumptions here
        (newAlreadySent, ServerReflectionResponse(req.host, Some(req), response))
      },
      _ => None)
  }

  private def toFileDescriptorResponse(
      fileDescriptor: Option[FileDescriptor],
      alreadySent: Set[String]): (Set[String], Out.FileDescriptorResponse) = {
    fileDescriptor match {
      case None =>
        (alreadySent, Out.FileDescriptorResponse(FileDescriptorResponse()))
      case Some(file) =>
        val (newAlreadySent, files) = withTransitiveDeps(alreadySent, file)
        (newAlreadySent, Out.FileDescriptorResponse(FileDescriptorResponse(files.map(getProtoBytes))))
    }
  }

  private def withTransitiveDeps(
      alreadySent: Set[String],
      file: FileDescriptor): (Set[String], List[FileDescriptor]) = {
    @annotation.tailrec
    def iterate(
        sent: Set[String],
        results: List[FileDescriptor],
        toAdd: List[FileDescriptor]): (Set[String], List[FileDescriptor]) = {
      toAdd match {
        case Nil => (sent, results)
        case _   =>
          // Need to compute the new set of files sent before working out which dependencies to send, to ensure
          // we don't send any dependencies that are being sent in this iteration
          val nowSent = sent ++ toAdd.map(_.getName)
          val depsOfToAdd =
            toAdd.flatMap(_.getDependencies.asScala).distinct.filterNot(dep => nowSent.contains(dep.getName))
          iterate(nowSent, toAdd ::: results, depsOfToAdd)
      }
    }

    iterate(alreadySent, Nil, List(file))
  }

  private def getProtoBytes(fileDescriptor: FileDescriptor): ByteString =
    protoBytesLocalCache.getOrElseUpdate(fileDescriptor.getName, fileDescriptor.toProto.toByteString)
}

/**
 * INTERNAL API
 */
@InternalApi
object ServerReflectionImpl {
  import scala.jdk.CollectionConverters._

  def apply(fileDescriptors: Seq[FileDescriptor], services: List[String]): ServerReflectionImpl = {
    val fileDescriptorsWithDeps = (ReflectionProto.javaDescriptor +: fileDescriptors).toSet.flatMap(flattenDependencies)

    new ServerReflectionImpl(
      fileDescriptorsWithDeps.map(fd => fd.getName -> fd).toMap,
      ServerReflection.name +: services)
  }

  private def flattenDependencies(descriptor: FileDescriptor): Set[FileDescriptor] = {
    descriptor.getDependencies.asScala.toSet.flatMap(flattenDependencies) + descriptor
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
