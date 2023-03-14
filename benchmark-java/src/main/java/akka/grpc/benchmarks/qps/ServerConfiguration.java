/*
 * Copyright (C) 2018-2023 Lightbend Inc. <http://www.lightbend.com>
 * Copyright 2015, gRPC Authors All rights reserved.
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

package akka.grpc.benchmarks.qps;

import akka.grpc.benchmarks.SocketAddressValidator;
import akka.grpc.benchmarks.Utils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

import static akka.grpc.benchmarks.Utils.parseBoolean;
import static java.lang.Integer.parseInt;

/**
 * Configuration options for benchmark servers.
 */
class ServerConfiguration implements Configuration {
  private static final ServerConfiguration DEFAULT = new ServerConfiguration();

  Transport transport = Transport.AKKA_HTTP;
  boolean tls;
  SocketAddress address;

  private ServerConfiguration() {
  }

  static Builder newBuilder() {
    return new Builder();
  }

  static class Builder extends AbstractConfigurationBuilder<ServerConfiguration> {
    private static final List<Param> PARAMS = supportedParams();

    private Builder() {
    }

    @Override
    protected ServerConfiguration newConfiguration() {
      return new ServerConfiguration();
    }

    @Override
    protected Collection<Param> getParams() {
      return PARAMS;
    }

    @Override
    protected ServerConfiguration build0(ServerConfiguration config) {
      if (config.tls && !config.transport.tlsSupported) {
        throw new IllegalArgumentException(
            "TLS unsupported with the " + config.transport.name().toLowerCase() + " transport");
      }

      // Verify that the address type is correct for the transport type.
      config.transport.validateSocketAddress(config.address);
      return config;
    }

    private static List<Param> supportedParams() {
      return Collections.unmodifiableList(new ArrayList<Param>(
          Arrays.asList(ServerParam.values())));
    }
  }

  /**
   * All of the supported transports.
   */
  public enum Transport {
    AKKA_HTTP(true, "The Akka HTTP transport.", SocketAddressValidator.INET);

    private final boolean tlsSupported;
    private final String description;
    private final SocketAddressValidator socketAddressValidator;

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
    void validateSocketAddress(SocketAddress address) {
      if (!socketAddressValidator.isValidSocketAddress(address)) {
        throw new IllegalArgumentException(
            "Invalid address " + address + " for transport " + this);
      }
    }

    static String getDescriptionString() {
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

  enum ServerParam implements AbstractConfigurationBuilder.Param {
    ADDRESS("STR", "Socket address (host:port).", null, true) {
      @Override
      protected void setServerValue(ServerConfiguration config, String value) {
        SocketAddress address = Utils.parseSocketAddress(value);
        if (address instanceof InetSocketAddress) {
          InetSocketAddress addr = (InetSocketAddress) address;
          int port = addr.getPort() == 0 ? Utils.pickUnusedPort() : addr.getPort();
          // Re-create the address so that the server is available on all local addresses.
          address = new InetSocketAddress(port);
        }
        config.address = address;
      }
    },
    TLS("", "Enable TLS.", "" + DEFAULT.tls) {
      @Override
      protected void setServerValue(ServerConfiguration config, String value) {
        config.tls = parseBoolean(value);
      }
    },
    TRANSPORT("STR", Transport.getDescriptionString(), DEFAULT.transport.name().toLowerCase()) {
      @Override
      protected void setServerValue(ServerConfiguration config, String value) {
        config.transport = Transport.valueOf(value.toUpperCase());
      }
    };

    private final String type;
    private final String description;
    private final String defaultValue;
    private final boolean required;

    ServerParam(String type, String description, String defaultValue) {
      this(type, description, defaultValue, false);
    }

    ServerParam(String type, String description, String defaultValue, boolean required) {
      this.type = type;
      this.description = description;
      this.defaultValue = defaultValue;
      this.required = required;
    }

    @Override
    public String getName() {
      return name().toLowerCase();
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getDefaultValue() {
      return defaultValue;
    }

    @Override
    public boolean isRequired() {
      return required;
    }

    @Override
    public void setValue(Configuration config, String value) {
      setServerValue((ServerConfiguration) config, value);
    }

    protected abstract void setServerValue(ServerConfiguration config, String value);
  }
}
