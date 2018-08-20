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

import org.scalatest.{ TestData, TestSuite, TestSuiteMixin }
import play.api.Application
import play.api.test.{ DefaultTestServerFactory, NewTestServer, ServerEndpoints }

trait NewBaseOneServerPerTest extends TestSuiteMixin with NewServerProvider { this: TestSuite with FakeApplicationFactory =>

  @volatile private var privateApp: Application = _
  @volatile private var testServer: NewTestServer = _

  /**
   * Implicit method that returns the `Application` instance for the current test.
   */
  implicit final def app: Application = privateApp

  implicit final def serverEndpoints: ServerEndpoints = testServer.endpoints

  /**
   * Creates new instance of `Application` with parameters set to their defaults. Override this method if you
   * need an `Application` created with non-default parameter values.
   */
  def newAppForTest(testData: TestData): Application = fakeApplication()

  protected def newServerForTest(app: Application, testData: TestData): NewTestServer =
    DefaultTestServerFactory.start(app)

  /**
   * Creates new `Application` and running `TestServer` instances before executing each test, and
   * ensures they are cleaned up after the test completes. You can access the `Application` from
   * your tests as `app` and the `TestServer`'s port number as `port`.
   *
   * @param test the no-arg test function to run with a fixture
   * @return the `Outcome` of the test execution
   */
  abstract override def withFixture(test: NoArgTest) = {
    // Need to synchronize within a suite because we store current app/server in fields in the class
    // Could possibly pass app/server info in a ScalaTest object?
    synchronized {
      privateApp = newAppForTest(test)
      testServer = newServerForTest(app, test)
      try super.withFixture(test) finally {
        val ts = testServer // Store before nulling fields
        privateApp = null
        testServer = null
        // Stop server and release locks
        ts.stopServer.close()

      }
    }
  }
}
