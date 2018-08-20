/*
 * Copyright 2001-2016 Artima, Inc.
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
package org.scalatestplus.play

import play.api.Application
import play.api.test.ServerEndpoints

trait NewServerProvider {

  /**
   * Implicit method that returns a `Application` instance.
   */
  implicit def app: Application

  implicit protected def serverEndpoints: ServerEndpoints

  /**
   * The port used by the `TestServer`.
   */
  // TODO: Document that this has been converted to a final method
  final def port: Int = portNumber.value

  /**
   * Implicit `PortNumber` instance that wraps `port`. The value returned from `portNumber.value`
   * will be same as the value of `port`.
   *
   * @return the configured port number, wrapped in a `PortNumber`
   */
  implicit def portNumber: PortNumber = PortNumber(serverEndpoints.httpEndpoint.get.port)
}
