/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.test

/**
 * This is a simple wrapper around Play's [[TestServer]] class.
 * The fields in the NewTestServer class will eventually be merged into
 * Play's TestServer class.
 *
 * @param testServer The object we're adding fields to.
 * @param endpoints The list of endpoints for the running server.
 * @param stopServer A handle that can be used to close the server.
 */
// TODO: Merge this functionality into TestServer itself
final case class NewTestServer(
  testServer: TestServer,
  endpoints: ServerEndpoints,
  stopServer: AutoCloseable)
