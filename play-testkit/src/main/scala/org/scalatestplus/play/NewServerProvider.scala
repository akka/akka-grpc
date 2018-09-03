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
import play.api.test.RunningServer

// RICH: A new version of scalatestplusplay's ServerProvider.
// The changes should be merged into scalatestplusplay.
trait NewServerProvider {

  /**
   * Implicit method that returns a `Application` instance.
   */
  implicit def app: Application

  /**
   * The endpoints of the running test server.
   * @return
   */
  // RICH: new property
  implicit protected def runningServer: RunningServer

  /**
   * The port used by the `TestServer`.
   */
  // RICH: changed to final because most people read this rather than override it
  // TODO: Document that this has been converted to a final method
  final def port: Int = portNumber.value

  /**
   * Implicit `PortNumber` instance that wraps `port`. The value returned from `portNumber.value`
   * will be same as the value of `port`.
   *
   * @return the configured port number, wrapped in a `PortNumber`
   */
  implicit def portNumber: PortNumber = {
    PortNumber(runningServer.endpoints.httpEndpoint.fold(throw new IllegalStateException("No HTTP port available for test server"))(_.port))
  }
}
