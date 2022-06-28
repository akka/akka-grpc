/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import com.google.protobuf.descriptor.{ MethodOptions => spbMethodOptions }
import com.google.protobuf.Descriptors.MethodDescriptor
import com.google.protobuf.descriptor.MethodOptions.IdempotencyLevel
import com.google.protobuf.descriptor.MethodOptions.IdempotencyLevel.{
  IDEMPOTENCY_UNKNOWN,
  IDEMPOTENT,
  NO_SIDE_EFFECTS,
  Unrecognized
}

import scala.collection.JavaConverters._

private[grpc] object Options {

  private def fromValue(__value: _root_.scala.Int): IdempotencyLevel = __value match {
    case 0       => IDEMPOTENCY_UNKNOWN
    case 1       => NO_SIDE_EFFECTS
    case 2       => IDEMPOTENT
    case __other => Unrecognized(__other)
  }
  private def fromJavaValue(
      pbJavaSource: com.google.protobuf.DescriptorProtos.MethodOptions.IdempotencyLevel): IdempotencyLevel = fromValue(
    pbJavaSource.getNumber)

  private def fromJavaProto(
      javaPbSource: com.google.protobuf.DescriptorProtos.MethodOptions): com.google.protobuf.descriptor.MethodOptions =
    com.google.protobuf.descriptor.MethodOptions(
      deprecated = if (javaPbSource.hasDeprecated) Some(javaPbSource.getDeprecated.booleanValue) else _root_.scala.None,
      idempotencyLevel =
        if (javaPbSource.hasIdempotencyLevel) Some(fromJavaValue(javaPbSource.getIdempotencyLevel))
        else _root_.scala.None,
      uninterpretedOption = javaPbSource.getUninterpretedOptionList.asScala.iterator.map(fromJavaProto(_)).toSeq)

  private def fromJavaProto(javaPbSource: com.google.protobuf.DescriptorProtos.UninterpretedOption.NamePart)
      : com.google.protobuf.descriptor.UninterpretedOption.NamePart = com.google.protobuf.descriptor.UninterpretedOption
    .NamePart(namePart = javaPbSource.getNamePart, isExtension = javaPbSource.getIsExtension.booleanValue)

  private def fromJavaProto(javaPbSource: com.google.protobuf.DescriptorProtos.UninterpretedOption)
      : com.google.protobuf.descriptor.UninterpretedOption = com.google.protobuf.descriptor.UninterpretedOption(
    name = javaPbSource.getNameList.asScala.iterator.map(fromJavaProto(_)).toSeq,
    identifierValue = if (javaPbSource.hasIdentifierValue) Some(javaPbSource.getIdentifierValue) else _root_.scala.None,
    positiveIntValue =
      if (javaPbSource.hasPositiveIntValue) Some(javaPbSource.getPositiveIntValue.longValue) else _root_.scala.None,
    negativeIntValue =
      if (javaPbSource.hasNegativeIntValue) Some(javaPbSource.getNegativeIntValue.longValue) else _root_.scala.None,
    doubleValue = if (javaPbSource.hasDoubleValue) Some(javaPbSource.getDoubleValue.doubleValue) else _root_.scala.None,
    stringValue = if (javaPbSource.hasStringValue) Some(javaPbSource.getStringValue) else _root_.scala.None,
    aggregateValue = if (javaPbSource.hasAggregateValue) Some(javaPbSource.getAggregateValue) else _root_.scala.None)

  /**
   * ScalaPB doesn't do this conversion for us unfortunately.
   * By doing it, we can use HttpProto.entityKey.get() to read the entity key nicely.
   */
  final def convertMethodOptions(method: MethodDescriptor): spbMethodOptions =
    fromJavaProto(method.toProto.getOptions)
      .withUnknownFields(scalapb.UnknownFieldSet(method.getOptions.getUnknownFields.asMap.asScala.map {
        case (idx, f) =>
          idx.toInt -> scalapb.UnknownFieldSet.Field(
            varint = f.getVarintList.iterator.asScala.map(_.toLong).toSeq,
            fixed64 = f.getFixed64List.iterator.asScala.map(_.toLong).toSeq,
            fixed32 = f.getFixed32List.iterator.asScala.map(_.toInt).toSeq,
            lengthDelimited = f.getLengthDelimitedList.asScala.toSeq)
      }.toMap))

}
