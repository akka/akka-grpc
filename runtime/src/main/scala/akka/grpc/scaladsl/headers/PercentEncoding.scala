/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 * Copyright 2016, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.grpc.scaladsl.headers

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Excerpt from
 * https://github.com/grpc/grpc/blob/7c9e8b425166276232653725de32ea0422a39b33/doc/PROTOCOL-HTTP2.md#responses:
 *
 * The value portion of Status-Message is conceptually a Unicode string description of the error, physically encoded as
 * UTF-8 followed by percent-encoding. Percent-encoding is specified in RFC 3986 §2.1, although the form used here has
 * different restricted characters. When decoding invalid values, implementations MUST NOT error or throw away the
 * message. At worst, the implementation can abort decoding the status message altogether such that the user would
 * received the raw percent-encoded form. Alternatively, the implementation can decode valid portions while leaving
 * broken %-encodings as-is or replacing them with a replacement character (e.g., '?' or the Unicode replacement
 * character).
 */
private[grpc] object PercentEncoding {
  // Copied with slight adaptations from https://github.com/grpc/grpc-java/blob/79e75bace40cea7e4be72e7dcd1f41c3ad6ee857/api/src/main/java/io/grpc/Status.java#L582
  object Encoder {
    private val HexArr: Array[Byte] =
      Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    def encode(value: String): String = {
      val valueBytes = value.getBytes(StandardCharsets.UTF_8)
      val firstIndexToEscape = valueBytes.indexWhere(isEscapingChar(_))
      if (firstIndexToEscape == -1) {
        // Why not return original input?
        // The following is not always true for any value: `new String(value.getBytes(StandardCharsets.UTF_8)) == value`
        // Consider "a \ud801": its underlying array of bytes is [97, 0, 32, 0, 1, -40] while "a \ud801".getBytes(StandardCharsets.UTF_8) yields [97, 32, 63]
        // There are 2 effects of decoding: ascii characters are encoded with 1 byte and lone surrogate has been replaced with ? (63 in ascii)
        new String(valueBytes, StandardCharsets.US_ASCII)
      } else
        encodeSlow(valueBytes, firstIndexToEscape)
    }

    private def isEscapingChar(b: Byte): Boolean = b < ' ' || b >= '~' || b == '%'

    private def encodeSlow(valueBytes: Array[Byte], riArg: Int): String = {
      var ri = riArg
      val escapedBytes = new Array[Byte](riArg + ((valueBytes.length - riArg) * 3))
      // copy over the good bytes
      if (riArg != 0) System.arraycopy(valueBytes, 0, escapedBytes, 0, riArg)
      var wi = ri

      while (ri < valueBytes.length) {
        val b = valueBytes(ri)
        // Manually implement URL encoding, per the gRPC spec.
        if (isEscapingChar(b)) {
          escapedBytes.update(wi, '%')
          escapedBytes.update(wi + 1, HexArr((b >> 4) & 0xf))
          escapedBytes.update(wi + 2, HexArr(b & 0xf))
          wi += 3
        } else {
          escapedBytes.update(wi, b)
          wi += 1
        }
        ri += 1
      }
      new String(escapedBytes, 0, wi, StandardCharsets.US_ASCII)
    }
  }

  // Copied with slight adaptations from https://github.com/grpc/grpc-java/blob/79e75bace40cea7e4be72e7dcd1f41c3ad6ee857/api/src/main/java/io/grpc/Status.java#L626
  object Decoder {
    private val TransferEncoding = StandardCharsets.US_ASCII

    def decode(value: String): String =
      if (value.indexOf('%') > -1)
        decodeSlow(value)
      else
        value

    private def decodeSlow(value: String): String = {
      val source = value.getBytes(TransferEncoding)
      val buf = ByteBuffer.allocate(source.length)
      var i = 0
      while (i < source.length) {
        if (source(i) == '%' && i + 2 < source.length) {
          val ch0 = Character.digit(source(i + 1), 16)
          val ch1 = Character.digit(source(i + 2), 16)
          if (ch0 > -1 && ch1 > -1) {
            val res = (ch0 << 4) + ch1
            buf.put(res.toByte)
            i += 3
          } else {
            buf.put(source(i))
            i += 1
          }
        } else {
          buf.put(source(i))
          i += 1
        }
      }
      new String(buf.array, 0, buf.position(), StandardCharsets.UTF_8)
    }
  }

}
