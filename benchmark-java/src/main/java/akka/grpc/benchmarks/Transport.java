/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
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

package akka.grpc.benchmarks;

import java.net.SocketAddress;

/**
 * All of the supported transports.
 */
public enum Transport {
  AKKA_HTTP(true, "The Akka HTTP transport.", SocketAddressValidator.INET);

  public final boolean tlsSupported;
  final String description;
  final SocketAddressValidator socketAddressValidator;

  Transport(boolean tlsSupported, String description,
            SocketAddressValidator socketAddressValidator) {
    this.tlsSupported = tlsSupported;
    this.description = description;
    this.socketAddressValidator = socketAddressValidator;
  }

  /**
   * Validates the given address for this transport.
   *
   * @throws IllegalArgumentException if the given address is invalid for this transport.
   */
  public void validateSocketAddress(SocketAddress address) {
    if (!socketAddressValidator.isValidSocketAddress(address)) {
      throw new IllegalArgumentException(
          "Invalid address " + address + " for transport " + this);
    }
  }

  /**
   * Describe the {@link Transport}.
   */
  public static String getDescriptionString() {
    StringBuilder builder = new StringBuilder("Select the transport to use. Options:\n");
    boolean first = true;
    for (Transport transport : Transport.values()) {
      if (!first) {
        builder.append("\n");
      }
      builder.append(transport.name().toLowerCase());
      builder.append(": ");
      builder.append(transport.description);
      first = false;
    }
    return builder.toString();
  }
}
