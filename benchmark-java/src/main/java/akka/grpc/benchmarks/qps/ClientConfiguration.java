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

import akka.grpc.benchmarks.Transport;
import akka.grpc.benchmarks.Utils;
import akka.grpc.benchmarks.proto.Control.RpcType;
import akka.grpc.benchmarks.proto.Messages;
import akka.grpc.benchmarks.proto.Messages.PayloadType;
import io.grpc.internal.testing.TestUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static akka.grpc.benchmarks.Utils.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;

/**
 * Configuration options for benchmark clients.
 */
public class ClientConfiguration implements Configuration {
  private static final ClientConfiguration DEFAULT = new ClientConfiguration();

  Transport transport = Transport.AKKA_HTTP;
  boolean tls = false;
  // currently we only support testca
  boolean testca = true;
  String authorityOverride = TestUtils.TEST_SERVER_HOST;
  SocketAddress address;
  int channels = 4;
  int outstandingRpcsPerChannel = 10;
  int serverPayload;
  int clientPayload;
  // seconds
  int duration = 60;
  // seconds
  int warmupDuration = 10;
  // currently not used, but we could port the grpc-java OpenLoopClient if we are interested in such test
  int targetQps;
  String histogramFile;
  RpcType rpcType = RpcType.UNARY;
  PayloadType payloadType = PayloadType.COMPRESSABLE;

  private ClientConfiguration() {
  }

  public Messages.SimpleRequest newRequest() {
    return Utils.makeRequest(payloadType, clientPayload, serverPayload);
  }

  /**
   * Constructs a builder for configuring a client application with supported parameters. If no
   * parameters are provided, all parameters are assumed to be supported.
   */
  static Builder newBuilder(ClientParam... supportedParams) {
    return new Builder(supportedParams);
  }

  static final class Builder extends AbstractConfigurationBuilder<ClientConfiguration> {
    private final Collection<Param> supportedParams;

    private Builder(ClientParam... supportedParams) {
      this.supportedParams = supportedOptionsSet(supportedParams);
    }

    @Override
    protected ClientConfiguration newConfiguration() {
      return new ClientConfiguration();
    }

    @Override
    protected Collection<Param> getParams() {
      return supportedParams;
    }

    @Override
    protected ClientConfiguration build0(ClientConfiguration config) {
      if (config.tls) {
        if (!config.transport.tlsSupported) {
          throw new IllegalArgumentException(
              "Transport " + config.transport.name().toLowerCase() + " does not support TLS.");
        }

        /*
        if (config.testca && config.address instanceof InetSocketAddress) {
          // Override the socket address with the host from the testca.
          InetSocketAddress address = (InetSocketAddress) config.address;
          config.address = TestUtils.testServerAddress(address.getHostName(),
                  address.getPort());
        }
        */
      }

      // Verify that the address type is correct for the transport type.
      config.transport.validateSocketAddress(config.address);

      return config;
    }

    private static Set<Param> supportedOptionsSet(ClientParam... supportedParams) {
      if (supportedParams.length == 0) {
        // If no options are supplied, default to including all options.
        supportedParams = ClientParam.values();
      }
      return Collections.unmodifiableSet(new LinkedHashSet<Param>(asList(supportedParams)));
    }
  }

  enum ClientParam implements AbstractConfigurationBuilder.Param {
    ADDRESS("STR", "Socket address (host:port) of the server.", null, true) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.address = Utils.parseSocketAddress(value);
      }
    },
    CHANNELS("INT", "Number of Channels.", "" + DEFAULT.channels) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.channels = parseInt(value);
      }
    },
    OUTSTANDING_RPCS("INT", "Number of outstanding RPCs per Channel.",
        "" + DEFAULT.outstandingRpcsPerChannel) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.outstandingRpcsPerChannel = parseInt(value);
      }
    },
    CLIENT_PAYLOAD("BYTES", "Payload Size of the Request.", "" + DEFAULT.clientPayload) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.clientPayload = parseInt(value);
      }
    },
    SERVER_PAYLOAD("BYTES", "Payload Size of the Response.", "" + DEFAULT.serverPayload) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.serverPayload = parseInt(value);
      }
    },
    TLS("", "Enable TLS.", "" + DEFAULT.tls) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.tls = parseBoolean(value);
      }
    },
    TESTCA("", "Use the provided Test Certificate for TLS.", "" + DEFAULT.testca) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.testca = parseBoolean(value);
      }
    },
    TRANSPORT("STR", Transport.getDescriptionString(), DEFAULT.transport.name().toLowerCase()) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.transport = Transport.valueOf(value.toUpperCase());
      }
    },
    DURATION("SECONDS", "Duration of the benchmark.", "" + DEFAULT.duration) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.duration = parseInt(value);
      }
    },
    WARMUP_DURATION("SECONDS", "Warmup Duration of the benchmark.", "" + DEFAULT.warmupDuration) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.warmupDuration = parseInt(value);
      }
    },
    SAVE_HISTOGRAM("FILE", "Write the histogram with the latency recordings to file.", null) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.histogramFile = value;
      }
    },
    STREAMING_RPCS("", "Use Streaming RPCs.", "false") {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.rpcType = RpcType.STREAMING;
      }
    },
    TARGET_QPS("INT", "Average number of QPS to shoot for.", "" + DEFAULT.targetQps, true) {
      @Override
      protected void setClientValue(ClientConfiguration config, String value) {
        config.targetQps = parseInt(value);
      }
    };

    private final String type;
    private final String description;
    private final String defaultValue;
    private final boolean required;

    ClientParam(String type, String description, String defaultValue) {
      this(type, description, defaultValue, false);
    }

    ClientParam(String type, String description, String defaultValue, boolean required) {
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
      setClientValue((ClientConfiguration) config, value);
    }

    protected abstract void setClientValue(ClientConfiguration config, String value);
  }
}
